// TrueSight's own confirmation window, in place of window.confirm, which some browsers never show (answering Cancel).
//
//   const { confirm, dialog } = useConfirm();
//   if (await confirm({ title: 'Delete Portfolio', message: '…', confirmLabel: 'Delete', danger: true })) { … }
//   …and render {dialog}.
//
// Cancel has the focus first, so Enter never confirms by accident. Escape or a click outside cancels. Tab goes between
// the two buttons only. It opens above any other window, and gives focus back to whatever had it before.
import { type KeyboardEvent, type ReactNode, useCallback, useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

export type ConfirmOptions = { title: string; message: string; confirmLabel: string; danger?: boolean };

type Pending = { options: ConfirmOptions; resolve: (confirmed: boolean) => void };

export function useConfirm(): { confirm: (options: ConfirmOptions) => Promise<boolean>; dialog: ReactNode } {
  const [pending, setPending] = useState<Pending | null>(null);

  const confirm = useCallback(
    (options: ConfirmOptions) => new Promise<boolean>((resolve) => setPending({ options, resolve })),
    [],
  );

  const dialog = pending ? (
    <ConfirmDialog
      options={pending.options}
      onAnswer={(confirmed) => {
        pending.resolve(confirmed);
        setPending(null);
      }}
    />
  ) : null;

  return { confirm, dialog };
}

function ConfirmDialog({ options, onAnswer }: { options: ConfirmOptions; onAnswer: (confirmed: boolean) => void }) {
  const titleId = useId();
  const messageId = useId();
  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const answerRef = useRef(onAnswer);
  answerRef.current = onAnswer;

  useEffect(() => {
    const before = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    cancelRef.current?.focus();
    const cancelOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') answerRef.current(false);
    };
    document.addEventListener('keydown', cancelOnEscape);
    return () => {
      document.removeEventListener('keydown', cancelOnEscape);
      if (before?.isConnected) before.focus();
    };
  }, []);

  // Tab and Shift+Tab go between Cancel and the action, never to anything behind, including another window.
  function keepFocusInside(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Tab') return;
    event.preventDefault();
    event.stopPropagation();
    (document.activeElement === cancelRef.current ? confirmRef : cancelRef).current?.focus();
  }

  return createPortal(
    <div
      className="window-backdrop confirm-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onAnswer(false)}
    >
      <div
        className="window confirm-window"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={messageId}
        onKeyDown={keepFocusInside}
      >
        <h2 id={titleId}>{options.title}</h2>
        <p id={messageId}>{options.message}</p>
        <div className="confirm-actions">
          <button ref={cancelRef} type="button" className="secondary" onClick={() => onAnswer(false)}>
            Cancel
          </button>
          <button
            ref={confirmRef}
            type="button"
            className={options.danger ? 'danger' : undefined}
            onClick={() => onAnswer(true)}
          >
            {options.confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
