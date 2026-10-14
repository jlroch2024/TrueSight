import type { ChangeEvent, FocusEvent, InputHTMLAttributes } from 'react';

type FormFieldProps = {
  id: string;
  label: string;
  value: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onBlur: (event: FocusEvent<HTMLInputElement>) => void;
  error?: string | null;
  hint?: string;
} & Pick<InputHTMLAttributes<HTMLInputElement>, 'type' | 'autoComplete'>;

export function FormField({ id, label, value, onChange, onBlur, error, hint, type = 'text', autoComplete }: FormFieldProps) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = error ? errorId : hint ? hintId : undefined;

  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        autoComplete={autoComplete}
        value={value}
        onChange={onChange}
        onBlur={onBlur}
        aria-invalid={Boolean(error)}
        aria-describedby={describedBy}
      />
      {error ? (
        <p className="field-error" id={errorId} role="alert">
          <span aria-hidden="true">⚠</span> {error}
        </p>
      ) : hint ? (
        <p className="field-hint muted small" id={hintId}>{hint}</p>
      ) : null}
    </div>
  );
}
