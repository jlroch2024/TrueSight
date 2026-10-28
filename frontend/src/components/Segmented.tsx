// Tabs or a filter as one toggle: every option the same width, the chosen one on a plate that slides to it (see
// useSlidingPlate, which moves it by a CSS transition so it keeps its pace while the page draws what the choice brings).
import type { ReactNode } from 'react';
import { useSlidingPlate } from './useSlidingPlate';

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  // label is what the option shows; name, when label holds more than words (a count), is what it is called.
  options: { value: T; label: ReactNode; name?: string }[];
  onChange: (value: T) => void;
  label: string;
}) {
  const { container, plate } = useSlidingPlate<HTMLDivElement>(value);
  return (
    <div ref={container} className="seg" role="tablist" aria-label={label}>
      <span ref={plate} className="seg-plate" aria-hidden="true" />
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="tab"
          aria-label={option.name}
          aria-selected={option.value === value}
          data-chosen={option.value === value}
          onClick={() => onChange(option.value)}
        >
          <span className="seg-label">{option.label}</span>
        </button>
      ))}
    </div>
  );
}
