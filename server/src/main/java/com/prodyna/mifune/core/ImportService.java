package com.prodyna.mifune.core;

/*-
 * #%L
 * prodyna-mifune-server
 * %%
 * Copyright (C) 2021 PRODYNA SE
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencsv.CSVReader;
import com.prodyna.json.converter.JsonTransformer;
import com.prodyna.mifune.core.json.JsonPathEditor;
import com.prodyna.mifune.core.schema.CypherIndexBuilder;
import com.prodyna.mifune.core.schema.CypherUpdateBuilder;
import com.prodyna.mifune.core.schema.GraphJsonBuilder;
import com.prodyna.mifune.core.schema.GraphModel;
import com.prodyna.mifune.domain.Domain;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.core.eventbus.EventBus;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.neo4j.driver.Driver;
import org.reactivestreams.FlowAdapters;

@ApplicationScoped
public class ImportService {

	@Inject
	protected Logger log;

	@Inject
	protected EventBus eventBus;

	@ConfigProperty(name = "mifune.upload.dir")
	protected String uploadDir;

	@Inject
	protected Driver driver;

	@Inject
	protected GraphService graphService;
	@Inject
	protected SourceService sourceService;

	// This Method should be split up into domain import and file import
	public Uni<String> runImport(UUID domainId) {
		log.debug("start import");
		var counter = new AtomicInteger();
		var graph = graphService.graph();
		var domain = graphService.fetchDomain(domainId);
		log.debugf("start import found domain %s", domain.getName());
		GraphModel graphModel = new GraphModel(graph);
		var cypher = new CypherUpdateBuilder(graphModel, domainId).getCypher();
		List<String> indexCyphers = new CypherIndexBuilder().getCypher(domainId, graph);
		log.info(cypher);

		ObjectNode jsonModel = new GraphJsonBuilder(graphModel, domainId, false).getJson();
		cleanJsonModel(domain, jsonModel);

		var transformer = new JsonTransformer(jsonModel, 1);
		var importFile = Paths.get(uploadDir, domain.getFile());
		var session = driver.asyncSession();

		// Create the domain Node
		var domainTask = Uni.createFrom()
				.completionStage(session
						.writeTransactionAsync(tx -> tx.runAsync("merge(d:Domain {id:$id}) set d.name = $name",
								Map.of("id", domain.getId().toString(), "name", domain.getName())))
						.thenCompose(r -> session.closeAsync().toCompletableFuture()));

		// Create Indexes on Import
		var indexTask = Multi.createFrom().iterable(indexCyphers).onItem().transformToUni(indexCypher -> {
			var s = driver.asyncSession();
			return Uni.createFrom()
					.completionStage(s.writeTransactionAsync(tx -> tx.runAsync(indexCypher)).exceptionally(e -> {
						log.error(" Failed creating index: " + e.getMessage());
						return null;
					}).thenCompose(x1 -> s.closeAsync()));
		});

		indexTask.withRequests(1).concatenate().subscribe().with(s -> log.info(" created Index: " + s),
				s -> log.error(" Failed creating Index: " + s.getMessage()),
				() -> log.info(" Creating Indexes: Done! "));

		// Create all nodes under this domain
		var importTask = Multi.createFrom().publisher(FlowAdapters.toProcessor(transformer))
				.emitOn(Infrastructure.getDefaultWorkerPool()).onItem().transformToUni(node -> {
					var entry = new ObjectMapper().convertValue(node, Map.class);
					var s = driver.asyncSession();
					return Uni.createFrom().completionStage(s
							.writeTransactionAsync(
									tx -> tx.runAsync(cypher, Map.of("model", entry, "domainId", domainId.toString())))
							.exceptionally(e -> {
								log.error(" Failed item import in file: " + domain.getFile() + " on line "
										+ counter.getAndIncrement() + " Message: " + e.getMessage());
								return null;
							}).thenCompose(x1 -> session.closeAsync()).thenApply(v -> counter.incrementAndGet()));

				});

		importTask.withRequests(1).concatenate().subscribe().with(s -> eventBus.publish(domainId.toString(), s),
				throwable -> log.error("error in pipeline: " + throwable.getMessage()), () -> log.info("done"));

		// this gets called before the above is finished
		return domainTask.onItem().invoke(() -> Infrastructure.getDefaultExecutor().execute(() -> {
			pipeFile(importFile, transformer::accept);
			transformer.onComplete();
		})).map(x -> "OK");

	}

	private void cleanJsonModel(Domain domain, ObjectNode jsonModel) {
		JsonPathEditor jsonPathEditor = new JsonPathEditor();
		var header = sourceService.fileHeader(domain.getFile());
		List<String> existingPath = jsonPathEditor.extractFieldPaths(jsonModel);
		existingPath.forEach(path -> {
			var value = domain.getColumnMapping().get(path);
			if (domain.getColumnMapping().containsKey(path) && Objects.nonNull(value)) {
				jsonPathEditor.update(jsonModel, path,
						String.valueOf(header.indexOf(value)) + ":" + jsonPathEditor.value(jsonModel, path).asText());
			} else {
				jsonPathEditor.remove(jsonModel, path);
			}
		});

		log.debugf("JsonModel: %s", jsonModel);
	}

	private void pipeFile(java.nio.file.Path importFile, Consumer<? super List<String>> consumer) {
		try {
			StreamSupport
					.stream(new CSVReader(new FileReader(importFile.toFile(), StandardCharsets.UTF_8)).spliterator(),
							false)
					.skip(1).map(Arrays::asList).forEach(consumer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
