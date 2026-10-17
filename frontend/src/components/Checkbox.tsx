// TrueSight's own checkbox: a square box with a teal tick, beside its label. A real checkbox sits underneath, hidden,
// so Space, forms and screen readers work as usual; only the drawn box is ours.
type Props = {
  id: string;
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
};

export function Checkbox({ id, label, checked, onChange, disabled }: Props) {
  return (
    <label className="checkbox" htmlFor={id}>
      <input
        id={id}
        type="checkbox"
        className="visually-hidden"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="checkbox-box" aria-hidden="true" />
      <span>{label}</span>
    </label>
  );
}
