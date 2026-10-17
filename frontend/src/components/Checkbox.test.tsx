import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Checkbox } from './Checkbox';

function renderCheckbox(onChange = vi.fn()) {
  function Harness() {
    const [checked, setChecked] = useState(false);
    return (
      <Checkbox
        id="show-dismissed"
        label="Show Dismissed"
        checked={checked}
        onChange={(next) => {
          setChecked(next);
          onChange(next);
        }}
      />
    );
  }
  render(<Harness />);
  return onChange;
}

describe('Checkbox', () => {
  it('toggles when its label is clicked, and reports the change', async () => {
    const onChange = renderCheckbox();
    const box = screen.getByRole('checkbox', { name: 'Show Dismissed' });

    await userEvent.click(screen.getByText('Show Dismissed'));
    expect(box).toBeChecked();
    expect(onChange).toHaveBeenLastCalledWith(true);

    await userEvent.click(screen.getByText('Show Dismissed'));
    expect(box).not.toBeChecked();
    expect(onChange).toHaveBeenLastCalledWith(false);
  });

  it('toggles with Space from the keyboard', async () => {
    const user = userEvent.setup();
    const onChange = renderCheckbox();

    await user.tab();
    expect(screen.getByRole('checkbox', { name: 'Show Dismissed' })).toHaveFocus();
    await user.keyboard(' ');

    expect(onChange).toHaveBeenCalledWith(true);
  });
});
