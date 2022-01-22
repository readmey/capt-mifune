export interface GraphStatistics {
  nodes: number
  relations: number
}

export interface Graph {
  domains: Domain[]
  nodes: Node[]
  relations: Relation[]
}

export interface GraphDelta {
  trigger: string
  changedDomains: Domain[]
  changedNodes: Node[]
  changedRelations: Relation[]
  removedDomains: string[]
  removedNodes: string[]
  removedRelations: string[]
}

export interface DomainCreate {
  name: string
  rootNodeId?: string
}

export interface DomainUpdate {
  name: string
  rootNodeId?: string
  file?: string
  columnMapping?: { [key: string]: string }
}

export interface Domain {
  id: string
  name: string
  rootNodeId?: string
  modelValid: boolean
  mappingValid: boolean
  file?: string
  columnMapping?: { [key: string]: string }
}

export interface NodeCreate {
  domainIds: string[]
  label: string
  color: string
  properties?: Property[]
}

export interface NodeUpdate {
  domainIds: string[]
  label: string
  color: string
  properties?: Property[]
}

export interface Node {
  id: string
  domainIds: string[]
  label: string
  color: string
  properties: Property[]
}

export interface RelationCreate {
  domainIds: string[]
  type: string
  sourceId: string
  targetId: string
  primary: boolean
  multiple: boolean
  properties?: Property[]
}

export interface RelationUpdate {
  domainIds: string[]
  type: string
  sourceId: string
  targetId: string
  primary: boolean
  multiple: boolean
  properties?: Property[]
}

export interface Relation {
  id: string
  domainIds: string[]
  type: string
  sourceId: string
  targetId: string
  primary: boolean
  multiple: boolean
  color: string
  properties: Property[]
}

export interface Property {
  name: string
  type: string
  primary: boolean
}

export interface Source {
  name: string
  header: string[]
}
export interface Filter {
  property: string
  value: number | string | undefined
}

export enum QueryFunctions {
  AVG = 'avg',
  COUNT = 'count',
  MAX = 'max',
  MIN = 'min',
  SUM = 'sum',
  VALUE = 'value',
  HIERARCHY = 'hierarchyCalculation',
}

export interface QueryResultDefinition {
  name: string
  function: QueryFunctions
  parameters: string[]
  uuid?: string
}
