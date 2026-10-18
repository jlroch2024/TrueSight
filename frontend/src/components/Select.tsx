// TrueSight's own drop-down list, used instead of the browser's own select list, so it matches the rest of the app.
//
// A button shows the chosen item; it opens a panel listing every item, the chosen one marked with a teal bar. It works
// from the keyboard as the browser's list does: on the button, Down, Up, Enter or Space opens the list; in the list, Up and Down
// move, Home and End jump, Enter or Space chooses, Escape or Tab closes, and typing a letter jumps to the next item
// starting with it. Clicking outside closes it. Screen readers hear a button that opens a list box.
import { type KeyboardEvent, useEffect, useId, useRef, useState } from 'react';

export type SelectOption = { value: string; label: string };

type Props = {
  id: string;
  label: string;
  value: string;
  options: SelectOption[];
  onChange: (value: string) => void;
};

export function Select({ id, label, value, options, onChange }: Props) {
  const [open, setOpen] = useState(false);
  // The item the keyboard is on while the list is open.
  const [active, setActive] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const listId = `${id}-list`;
  const labelId = `${id}-label`;
  const valueId = `${id}-value`;
  const optionId = useId();
  const chosenIndex = Math.max(0, options.findIndex((option) => option.value === value));
  const chosen = options[chosenIndex];

  // Clicking anywhere outside closes the list, without choosing.
  useEffect(() => {
    if (!open) return;
    const closeIfOutside = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', closeIfOutside);
    return () => document.removeEventListener('mousedown', closeIfOutside);
  }, [open]);

  // The list takes the focus when it opens, so the keys below reach it.
  useEffect(() => {
    if (open) listRef.current?.focus();
  }, [open]);

  // Keeps the item the keyboard is on in view in a long list.
  useEffect(() => {
    if (!open) return;
    const item = document.getElementById(`${optionId}-${active}`);
    item?.scrollIntoView?.({ block: 'nearest' });
  }, [open, active, optionId]);

  function openList() {
    setActive(chosenIndex);
    setOpen(true);
  }

  function close() {
    setOpen(false);
    buttonRef.current?.focus();
  }

  function choose(index: number) {
    const option = options[index];
    close();
    if (option && option.value !== value) onChange(option.value);
  }

  function onButtonKeyDown(event: KeyboardEvent) {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
      event.preventDefault();
      openList();
    }
  }

  function onListKeyDown(event: KeyboardEvent) {
    const last = options.length - 1;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        setActive((index) => Math.min(last, index + 1));
        return;
      case 'ArrowUp':
        event.preventDefault();
        setActive((index) => Math.max(0, index - 1));
        return;
      case 'Home':
        event.preventDefault();
        setActive(0);
        return;
      case 'End':
        event.preventDefault();
        setActive(last);
        return;
      case 'Enter':
      case ' ':
        event.preventDefault();
        choose(active);
        return;
      case 'Escape':
        event.preventDefault();
        close();
        return;
      case 'Tab':
        setOpen(false);
        return;
      default:
        if (event.key.length === 1 && /\S/.test(event.key)) {
          // The next item after the current one that starts with the letter, wrapping round to the top.
          const letter = event.key.toLowerCase();
          for (let step = 1; step <= options.length; step++) {
            const index = (active + step) % options.length;
            if (options[index]!.label.toLowerCase().startsWith(letter)) {
              setActive(index);
              return;
            }
          }
        }
    }
  }

  return (
    <div className="select" ref={rootRef}>
      <label id={labelId} htmlFor={id}>
        {label}
      </label>
      {/* The box and its list share this frame, so the open list has exactly the closed box's left edge and width. */}
      <div className="select-box">
        <button
          ref={buttonRef}
          id={id}
          type="button"
          className="select-button"
          aria-haspopup="listbox"
          // Named by its label and the chosen item, so a screen reader says "Open Portfolio, Semiconductors".
          aria-labelledby={`${labelId} ${valueId}`}
          aria-expanded={open}
          aria-controls={open ? listId : undefined}
          onClick={() => (open ? close() : openList())}
          onKeyDown={onButtonKeyDown}
        >
          <span className="select-values">
            <span id={valueId} className="select-value">{chosen?.label ?? ''}</span>
            {/* Every item's label, invisible, in the same spot: the box is as wide as the longest one, open or closed. */}
            {options.map((option) => (
              <span key={option.value} className="select-sizer" aria-hidden="true">
                {option.label}
              </span>
            ))}
          </span>
          <span className="select-chevron" aria-hidden="true" />
        </button>
        {open && (
          <ul
            ref={listRef}
            id={listId}
            role="listbox"
            tabIndex={-1}
            className="select-list"
            aria-labelledby={labelId}
            aria-activedescendant={`${optionId}-${active}`}
            onKeyDown={onListKeyDown}
          >
            {options.map((option, index) => (
              <li
                key={option.value}
                id={`${optionId}-${index}`}
                role="option"
                aria-selected={option.value === value}
                className={index === active ? 'active' : undefined}
                onMouseEnter={() => setActive(index)}
                onClick={() => choose(index)}
              >
                {option.label}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
