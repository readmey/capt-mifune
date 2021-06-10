import * as React from "react";
import { Form } from "react-bootstrap";

interface IFormSelectProps {
  title: string;
  options: string[];
  value?: string;
  onChangeHandler?: (event: any) => void;
}

const FormSelect: React.FunctionComponent<IFormSelectProps> = ({
  title,
  options,
  value,
  onChangeHandler,
}) => {
  const handleChange = (event: React.ChangeEvent) => {
    if (onChangeHandler) {
      onChangeHandler(event);
    }
  };

  return (
    <Form.Group controlId="exampleForm.ControlSelect1">
      <Form.Label>{title}</Form.Label>
      <Form.Control
        onChange={handleChange}
        as="select"
        value={value ? value : "None"}
      >
        {options.map((option: string) => {
          return <option key={option}>{option}</option>;
        })}
      </Form.Control>
    </Form.Group>
  );
};

export default FormSelect;
