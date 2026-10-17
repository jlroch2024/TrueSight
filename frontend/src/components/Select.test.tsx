// TrueSight's own drop-down list: mouse, keyboard, clicking outside, and what screen readers hear.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Select } from './Select';

const OPTIONS = [
  { value: '1', label: 'Consumer Goods' },
  { value: '2', label: 'Pharma and Biotech' },
  { value: '3', label: 'Semiconductors' },
];

function renderSelect(onChange = vi.fn()) {
  function Harness() {
    const [value, setValue] = useState('1');
    return (
      <>
        <Select
          id="portfolio"
          label="Open Portfolio"
          value={value}
          options={OPTIONS}
          onChange={(next) => {
            setValue(next);
            onChange(next);
          }}
        />
        <p>Somewhere else</p>
      </>
    );
  }
  render(<Harness />);
  return onChange;
}

describe('Select', () => {
  it('shows the chosen item on a labelled button, with no native select', () => {
    renderSelect();

    const button = screen.getByLabelText('Open Portfolio');
    expect(button).toHaveTextContent('Consumer Goods');
    expect(button).toHaveAccessibleName('Open Portfolio Consumer Goods');
    expect(button).toHaveAttribute('aria-haspopup', 'listbox');
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(document.querySelector('select')).toBeNull();
  });

  it('opens a list box with the chosen item selected, and chooses another by clicking', async () => {
    const onChange = renderSelect();

    await userEvent.click(screen.getByLabelText('Open Portfolio'));
    const list = screen.getByRole('listbox', { name: 'Open Portfolio' });
    expect(screen.getByRole('option', { name: 'Consumer Goods' })).toHaveAttribute('aria-selected', 'true');

    await userEvent.click(screen.getByRole('option', { name: 'Semiconductors' }));

    expect(onChange).toHaveBeenCalledWith('3');
    expect(list).not.toBeInTheDocument();
    expect(screen.getByLabelText('Open Portfolio')).toHaveTextContent('Semiconductors');
  });

  it('works from the keyboard alone: Down, Down, Enter chooses the third item', async () => {
    const user = userEvent.setup();
    const onChange = renderSelect();

    screen.getByLabelText('Open Portfolio').focus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('listbox')).toHaveFocus();
    await user.keyboard('{ArrowDown}{ArrowDown}{Enter}');

    expect(onChange).toHaveBeenCalledWith('3');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Open Portfolio')).toHaveFocus();
  });

  it('closes on Escape without changing, and gives the focus back to the button', async () => {
    const user = userEvent.setup();
    const onChange = renderSelect();

    await user.click(screen.getByLabelText('Open Portfolio'));
    await user.keyboard('{ArrowDown}{Escape}');

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Open Portfolio')).toHaveFocus();
  });

  it('jumps to the next item starting with a typed letter', async () => {
    const user = userEvent.setup();
    const onChange = renderSelect();

    await user.click(screen.getByLabelText('Open Portfolio'));
    await user.keyboard('s{Enter}');

    expect(onChange).toHaveBeenCalledWith('3');
  });

  it('closes when the user clicks outside it, without changing', async () => {
    const onChange = renderSelect();

    await userEvent.click(screen.getByLabelText('Open Portfolio'));
    await userEvent.click(screen.getByText('Somewhere else'));

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });
});
