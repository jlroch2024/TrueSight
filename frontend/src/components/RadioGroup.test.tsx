import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { RadioGroup } from './RadioGroup';

const OPTIONS = [
  { value: 'UNREVIEWED', label: 'Unreviewed' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'ALL', label: 'All' },
];

function renderGroup(onChange = vi.fn()) {
  function Harness() {
    const [value, setValue] = useState('UNREVIEWED');
    return (
      <RadioGroup
        name="filter"
        label="Show"
        value={value}
        options={OPTIONS}
        onChange={(next) => {
          setValue(next);
          onChange(next);
        }}
      />
    );
  }
  render(<Harness />);
  return onChange;
}

describe('RadioGroup', () => {
  it('is a group named by its label, with the chosen option checked', () => {
    renderGroup();

    expect(screen.getByRole('group', { name: 'Show' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Unreviewed' })).toBeChecked();
  });

  it('chooses an option when it is clicked, and reports the change', async () => {
    const onChange = renderGroup();

    await userEvent.click(screen.getByText('Approved'));

    expect(screen.getByRole('radio', { name: 'Approved' })).toBeChecked();
    expect(onChange).toHaveBeenCalledWith('APPROVED');
  });

  it('chooses with the arrow keys', async () => {
    const user = userEvent.setup();
    const onChange = renderGroup();

    await user.tab();
    await user.keyboard('{ArrowRight}');

    expect(onChange).toHaveBeenCalledWith('APPROVED');
    expect(screen.getByRole('radio', { name: 'Approved' })).toBeChecked();
  });
});
