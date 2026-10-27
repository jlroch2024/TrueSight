// The toggle every tab row and filter uses: its options are tabs, the chosen one marked for its sliding plate.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import styles from '../styles.css?raw';
import { Segmented } from './Segmented';

function Harness() {
  const [value, setValue] = useState<'open' | 'closed'>('open');
  return (
    <Segmented
      label="Show"
      value={value}
      onChange={setValue}
      options={[
        { value: 'open', name: 'Open (2)', label: <>Open <span className="count">2</span></> },
        { value: 'closed', name: 'Closed (1)', label: <>Closed <span className="count">1</span></> },
      ]}
    />
  );
}

describe('Segmented', () => {
  it('marksTheChosenOptionAndMovesToTheOneChosenNext', async () => {
    render(<Harness />);
    const open = screen.getByRole('tab', { name: 'Open (2)' });
    const closed = screen.getByRole('tab', { name: 'Closed (1)' });
    expect(screen.getByRole('tablist', { name: 'Show' })).toContainElement(open);
    expect(open).toHaveAttribute('aria-selected', 'true');
    expect(open).toHaveAttribute('data-chosen', 'true');

    await userEvent.click(closed);

    expect(closed).toHaveAttribute('aria-selected', 'true');
    expect(closed).toHaveAttribute('data-chosen', 'true');
    expect(open).toHaveAttribute('data-chosen', 'false');
  });

  it('givesEveryOptionTheSameWidthAndOnePlate', () => {
    const { container } = render(<Harness />);

    expect(container.querySelectorAll('.seg-plate')).toHaveLength(1);
    // Columns as wide as the widest option, or an equal share of the room.
    expect(styles).toMatch(/\.seg\s*\{[^}]*grid-auto-columns:\s*minmax\(max-content, 1fr\)/);
  });
});
