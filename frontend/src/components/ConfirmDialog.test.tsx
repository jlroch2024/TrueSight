// TrueSight's own confirmation window: what screen readers hear, where the focus goes, and every way to answer.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { useConfirm } from './ConfirmDialog';

function Harness() {
  const { confirm, dialog } = useConfirm();
  const [answer, setAnswer] = useState('none');
  return (
    <>
      <button
        type="button"
        onClick={async () => {
          const confirmed = await confirm({
            title: 'Delete Portfolio',
            message: 'Delete "Growth" and all its holdings? This cannot be undone.',
            confirmLabel: 'Delete',
            danger: true,
          });
          setAnswer(String(confirmed));
        }}
      >
        Ask
      </button>
      <button type="button">Behind</button>
      <output aria-label="Answer">{answer}</output>
      {dialog}
    </>
  );
}

async function ask() {
  render(<Harness />);
  await userEvent.click(screen.getByRole('button', { name: 'Ask' }));
  return screen.getByRole('alertdialog');
}

describe('ConfirmDialog', () => {
  it('is an alert dialog named by its title and described by its question, with a red action', async () => {
    const dialog = await ask();

    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName('Delete Portfolio');
    expect(dialog).toHaveAccessibleDescription('Delete "Growth" and all its holdings? This cannot be undone.');
    expect(screen.getByRole('button', { name: 'Delete' })).toHaveClass('danger');
  });

  it('gives Cancel the focus first, so Enter cancels', async () => {
    await ask();

    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
    await userEvent.keyboard('{Enter}');
    expect(screen.getByLabelText('Answer')).toHaveTextContent('false');
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('keeps Tab and Shift+Tab between its two buttons', async () => {
    const user = userEvent.setup();
    await ask();
    const cancel = screen.getByRole('button', { name: 'Cancel' });
    const remove = screen.getByRole('button', { name: 'Delete' });

    await user.tab();
    expect(remove).toHaveFocus();
    await user.tab();
    expect(cancel).toHaveFocus();
    await user.tab({ shift: true });
    expect(remove).toHaveFocus();
  });

  it('answers true from the action button, and gives the focus back', async () => {
    await ask();

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));

    expect(screen.getByLabelText('Answer')).toHaveTextContent('true');
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ask' })).toHaveFocus();
  });

  it('cancels on Cancel', async () => {
    await ask();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.getByLabelText('Answer')).toHaveTextContent('false');
  });

  it('cancels on Escape', async () => {
    await ask();

    await userEvent.keyboard('{Escape}');

    expect(screen.getByLabelText('Answer')).toHaveTextContent('false');
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('cancels on a click outside, but not on a click inside', async () => {
    const dialog = await ask();

    await userEvent.click(dialog);
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();

    await userEvent.click(dialog.parentElement!);
    expect(screen.getByLabelText('Answer')).toHaveTextContent('false');
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });
});
