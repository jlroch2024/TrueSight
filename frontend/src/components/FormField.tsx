import type { ChangeEvent, FocusEvent, InputHTMLAttributes } from 'react';

type FormFieldProps = {
  id: string;
  label: string;
  value: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onBlur: (event: FocusEvent<HTMLInputElement>) => void;
  error?: string | null;
} & Pick<InputHTMLAttributes<HTMLInputElement>, 'type' | 'autoComplete' | 'maxLength'>;

export function FormField({
  id,
  label,
  value,
  onChange,
  onBlur,
  error,
  type = 'text',
  autoComplete,
  maxLength,
}: FormFieldProps) {
  const errorId = `${id}-error`;
  const describedBy = error ? errorId : undefined;

  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        autoComplete={autoComplete}
        maxLength={maxLength}
        value={value}
        onChange={onChange}
        onBlur={onBlur}
        aria-invalid={Boolean(error)}
        aria-describedby={describedBy}
      />
      {error && (
        <p className="field-error" id={errorId} role="alert">
          <span aria-hidden="true">⚠</span> {error}
        </p>
      )}
    </div>
  );
}

/** An error about the whole form, such as a refused log in, in the same style as a field's error. */
export function FormError({ message }: { message: string }) {
  return (
    <p className="field-error form-error" role="alert">
      <span aria-hidden="true">⚠</span> {message}
    </p>
  );
}
