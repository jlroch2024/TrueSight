// TrueSight's own set of options: square options in a row, the chosen one filled teal. Real radio buttons sit
// underneath, hidden, so the arrow keys, forms and screen readers work as usual; only the drawn options are ours.
export type RadioOption = { value: string; label: string };

type Props = {
  name: string;
  label: string;
  value: string;
  options: RadioOption[];
  onChange: (value: string) => void;
};

export function RadioGroup({ name, label, value, options, onChange }: Props) {
  return (
    <fieldset className="radio-group">
      <legend>{label}</legend>
      <div className="radio-options">
        {options.map((option) => (
          <label key={option.value} className="radio-option">
            <input
              type="radio"
              className="visually-hidden"
              name={name}
              value={option.value}
              checked={option.value === value}
              onChange={() => onChange(option.value)}
            />
            <span>{option.label}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
