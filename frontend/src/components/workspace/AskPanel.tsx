// Ask TrueSight, in the workspace's bottom-middle slot: a question box, and above it the answers. An answer is in
// sentence case and cites what it rests on as buttons, each opening that relationship, holding or alert where the
// workspace shows it. The panel sits between the drawer and the Inspector and glides with them, as the toasts do.
import { ChevronDown, ChevronUp, LoaderCircle } from 'lucide-react';
import { type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { ASK_QUESTION_MAX, type AskSource } from '../../api/ask';
import type { AskExchange } from './useAsk';

// What the empty state offers to ask: a real question, sent as it is worded.
const SUGGESTION = { label: 'Which Holdings Share a Supplier', question: 'Which of my holdings share a supplier?' };

type Props = {
  exchanges: AskExchange[];
  sending: boolean;
  // The room the page leaves on each side, from its edge: the drawer's and the Inspector's.
  left: number;
  right: number;
  onAsk: (question: string) => void;
  onRetry: (id: number) => void;
  // Whether a source can still be opened (its item may be gone since the answer), and opening it.
  canOpen: (source: AskSource) => boolean;
  onOpen: (source: AskSource) => void;
};

export function AskPanel({ exchanges, sending, left, right, onAsk, onRetry, canOpen, onOpen }: Props) {
  const [text, setText] = useState('');
  const [open, setOpen] = useState(true);
  const log = useRef<HTMLDivElement>(null);
  const last = exchanges[exchanges.length - 1];

  // A new question or answer shows the conversation, scrolled to its end.
  useEffect(() => {
    if (exchanges.length > 0) setOpen(true);
  }, [exchanges.length]);
  useEffect(() => {
    if (log.current) log.current.scrollTop = log.current.scrollHeight;
  }, [exchanges, open]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (sending || !text.trim()) return;
    onAsk(text);
    setText('');
  }

  // Escape here closes the conversation, not the Inspector behind it: the page listens for Escape on the window.
  function keyDown(event: KeyboardEvent) {
    if (event.key !== 'Escape') return;
    event.stopPropagation();
    setOpen(false);
  }

  const showLog = exchanges.length > 0 && open;

  return (
    <section className="ask" aria-label="Ask TrueSight" style={{ left, right }}>
      <div className="ask-panel glass ticks" onKeyDown={keyDown}>
        {showLog && (
          <div ref={log} className="ask-log" role="log" aria-live="polite" aria-label="Conversation">
            {exchanges.map((exchange) => (
              <div key={exchange.id} className="ask-exchange">
                <p className="ask-question">{exchange.question}</p>
                {exchange.pending && (
                  <p className="ask-pending">
                    <LoaderCircle size={14} className="spin" /> Thinking
                  </p>
                )}
                {exchange.error && (
                  <div className="ask-failed">
                    <p role="alert" className="error">
                      {exchange.error}
                    </p>
                    <button type="button" className="btn small" disabled={sending} onClick={() => onRetry(exchange.id)}>
                      Retry
                    </button>
                  </div>
                )}
                {exchange.answer && (
                  <>
                    <p className="ask-answer">{exchange.answer.answer}</p>
                    {exchange.answer.sources.length > 0 && (
                      <ul className="ask-sources" aria-label="Sources">
                        {exchange.answer.sources.map((source) => (
                          <li key={`${source.kind}-${source.id}`}>
                            <button
                              type="button"
                              className="btn quiet small ask-source"
                              disabled={!canOpen(source)}
                              title={canOpen(source) ? source.label : 'No Longer Available'}
                              onClick={() => onOpen(source)}
                            >
                              {source.label}
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                  </>
                )}
              </div>
            ))}
          </div>
        )}
        <form className="ask-bar" onSubmit={submit}>
          <label htmlFor="ask-question">Ask TrueSight</label>
          <input
            id="ask-question"
            className="input"
            autoComplete="off"
            maxLength={ASK_QUESTION_MAX}
            // Until a question is asked, the bar is the empty state: what this is for, and a question to start with.
            placeholder={exchanges.length === 0 ? 'Ask About Your Portfolio' : undefined}
            value={text}
            onChange={(event) => setText(event.target.value)}
          />
          {exchanges.length === 0 && (
            <button type="button" className="btn quiet small ask-suggest" disabled={sending} onClick={() => onAsk(SUGGESTION.question)}>
              {SUGGESTION.label}
            </button>
          )}
          <button type="submit" className="btn primary" disabled={sending || !text.trim()}>
            {sending && <LoaderCircle size={15} className="spin" />}
            {sending ? 'Asking' : 'Ask'}
          </button>
          {last && (
            <button
              type="button"
              className="icon-btn"
              aria-label={open ? 'Hide the Conversation' : 'Show the Conversation'}
              title={open ? 'Hide the Conversation' : 'Show the Conversation'}
              onClick={() => setOpen(!open)}
            >
              {open ? <ChevronDown size={17} /> : <ChevronUp size={17} />}
            </button>
          )}
        </form>
      </div>
    </section>
  );
}
