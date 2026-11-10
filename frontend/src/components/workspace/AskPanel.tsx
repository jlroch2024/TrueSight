// Ask TrueSight, in the workspace's bottom-middle slot: a question box, and above it the answers. An answer is in
// sentence case and cites what it rests on as buttons, each opening that relationship, holding or alert where the
// workspace shows it. The panel sits between the drawer and the Inspector and glides with them, as the toasts do.
import { AnimatePresence, motion } from 'motion/react';
import { ChevronDown, ChevronUp, ScanSearch } from 'lucide-react';
import { type FormEvent, type KeyboardEvent, type ReactNode, useEffect, useRef, useState } from 'react';
import { ASK_QUESTION_MAX, type AskSource } from '../../api/ask';
import { FlowLink } from '../FlowLink';
import { PANEL_MOVE } from '../motion';
import { Tile } from '../Tile';
import { AskAnswerText, SUPPLIED, answerLines } from './askAnswer';
import type { AskExchange } from './useAsk';

// What the empty state offers to ask: a real question, sent as it is worded.
const SUGGESTION = { label: 'Which Holdings Share a Supplier', question: 'Which of my holdings share a supplier?' };

/** A company as a source's button draws it. */
export type AskParty = { name: string; displayName: string; label: string; ticker: string | null; holding: boolean; colour?: string };

/** What a source shows: a company, or a supplier and the customer it supplies. Null for what has no company (an alert). */
export type AskPicture = { from: AskParty; to?: AskParty } | null;

type Props = {
  exchanges: AskExchange[];
  sending: boolean;
  // The room the page leaves on each side, from its edge: the drawer's and the Inspector's.
  left: number;
  right: number;
  // Above the bottom row of the graph's controls, when the row has no room for the bar.
  bottom?: number;
  // False until the page knows where the box goes: until then it is not shown, so it never jumps into place.
  placed?: boolean;
  // Too little room in the row: a square of its icon, which opens the box (onUnfold); onFold when the box is left.
  folded?: boolean;
  onUnfold?: () => void;
  onFold?: () => void;
  // The company names an answer may mention, to pick out, and how to draw a source.
  names?: string[];
  picture?: (source: AskSource) => AskPicture;
  onAsk: (question: string) => void;
  onRetry: (id: number) => void;
  // Whether a source can still be opened (its item may be gone since the answer), and opening it.
  canOpen: (source: AskSource) => boolean;
  onOpen: (source: AskSource) => void;
};

export function AskPanel({ exchanges, sending, left, right, bottom, placed = true, folded = false, onUnfold, onFold, names = [], picture = () => null, onAsk, onRetry, canOpen, onOpen }: Props) {
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
  const section = useRef<HTMLElement>(null);
  // Opened from its square: a press anywhere outside it, with nothing typed, folds it back.
  useEffect(() => {
    if (!onFold || folded) return;
    const pressed = (event: PointerEvent) => {
      if (!text.trim() && section.current && !section.current.contains(event.target as Node)) onFold();
    };
    document.addEventListener('pointerdown', pressed);
    return () => document.removeEventListener('pointerdown', pressed);
  }, [onFold, folded, text]);
  // The rows below show who supplies whom, so a line saying the same (it names a row's supplier and says it supplies)
  // is left out. Every other line stays: the answer's own summary, an alert, a licence, the note that links are
  // unreviewed.
  const brief = (answer: string, sources: AskSource[]) => {
    const suppliers = sources.flatMap((source) => {
      const drawn = picture(source);
      return drawn?.to ? [drawn.from.displayName, drawn.from.label] : [];
    });
    if (suppliers.length === 0) return answer;
    const repeats = (line: string) => /\bsuppl(?:y|ies|ied)\b/i.test(line) && suppliers.some((name) => name && line.includes(name));
    const kept = answerLines(answer).filter((line, index) => index === 0 || !repeats(line));
    return kept.join(' ').replace(/;$/, '.');
  };
  // It glides with the panels only once it has first appeared where it belongs.
  const [settled, setSettled] = useState(false);
  useEffect(() => {
    if (!placed || settled) return;
    const frame = requestAnimationFrame(() => setSettled(true));
    return () => cancelAnimationFrame(frame);
  }, [placed, settled]);

  return (
    <section ref={section} className={`ask${placed ? '' : ' unplaced'}${settled ? '' : ' still'}`} aria-label="Ask TrueSight" style={{ left, right, bottom }}>
      {folded ? (
        <button
          type="button"
          className="ask-panel ask-fold glass ticks"
          aria-label="Ask TrueSight"
          title="Ask TrueSight"
          onClick={() => {
            onUnfold?.();
            window.setTimeout(() => document.getElementById('ask-question')?.focus(), 0);
          }}
        >
          <ScanSearch size={18} />
        </button>
      ) : (
      <div
        className="ask-panel glass ticks"
        onKeyDown={keyDown}
        // Opened from its square, it folds back when the keyboard takes the focus out of it with nothing typed. A press
        // outside it is caught below; a press on its own words gives no focus to anything, so it is not leaving.
        onBlur={(event) => {
          const to = event.relatedTarget as Node | null;
          if (onFold && to && !text.trim() && !event.currentTarget.contains(to)) onFold();
        }}
      >
        <AnimatePresence initial={false}>
        {showLog && (
          <motion.div
            key="log"
            className="ask-log-holder"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={PANEL_MOVE}
          >
          <div ref={log} className="ask-log" role="log" aria-live="polite" aria-label="Conversation">
            {exchanges.map((exchange) => (
              <div key={exchange.id} className="ask-exchange">
                <p className="ask-question">{exchange.question}</p>
                {exchange.pending && (
                  <p className="ask-pending">
                    <Pulse />
                    <span className="ask-thinking">Thinking</span>
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
                    <AskAnswerText answer={brief(exchange.answer.answer, exchange.answer.sources)} names={names} />
                    {exchange.answer.sources.length > 0 && (
                      <Sources sources={exchange.answer.sources} picture={picture} canOpen={canOpen} onOpen={onOpen} />
                    )}
                  </>
                )}
              </div>
            ))}
          </div>
          </motion.div>
        )}
        </AnimatePresence>
        <form className="ask-bar" onSubmit={submit}>
          <img className="ask-eye" src="/eye-logo.png" alt="" aria-hidden="true" />
          <input
            id="ask-question"
            className="input"
            aria-label="Ask TrueSight"
            autoComplete="off"
            maxLength={ASK_QUESTION_MAX}
            // Until a question is asked, the bar is the empty state: what this is for, and a question to start with.
            placeholder={exchanges.length === 0 ? 'Ask TrueSight About Your Portfolio' : 'Ask TrueSight'}
            value={text}
            onChange={(event) => setText(event.target.value)}
          />
          {exchanges.length === 0 && (
            <button type="button" className="btn quiet small ask-suggest" disabled={sending} onClick={() => onAsk(SUGGESTION.question)}>
              {SUGGESTION.label}
            </button>
          )}
          <button
            type="submit"
            className="btn primary ask-send"
            disabled={sending || !text.trim()}
            aria-label={sending ? 'Asking' : 'Ask'}
            title={sending ? 'Asking' : 'Ask'}
          >
            {sending ? <Pulse /> : <ScanSearch size={16} />}
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
      )}
    </section>
  );
}

/** A company in a source row: its tile, and its ticker (or short name) under it. */
function Party({ party }: { party: AskParty }) {
  return (
    <span className="ask-party">
      <Tile name={party.name} ticker={party.ticker} holding={party.holding} colour={party.colour} size={party.holding ? 34 : 28} />
      <small>{party.label}</small>
    </span>
  );
}

/**
 * What an answer rests on, one row per supplier: the supplier, the graph's arrow, then every customer the answer names
 * for it, each a button that opens that link. A holding on its own and an alert follow, as buttons of their own.
 */
function Sources({
  sources,
  picture,
  canOpen,
  onOpen,
}: {
  sources: AskSource[];
  picture: (source: AskSource) => AskPicture;
  canOpen: (source: AskSource) => boolean;
  onOpen: (source: AskSource) => void;
}) {
  const rows = new Map<string, { supplier: AskParty; links: { source: AskSource; customer: AskParty }[] }>();
  const others: { source: AskSource; drawn: AskPicture }[] = [];
  for (const source of sources) {
    const drawn = picture(source);
    if (drawn?.to) {
      // One row per supplier as people know it (a company can be stored under two names), each customer once.
      const row = rows.get(drawn.from.displayName) ?? { supplier: drawn.from, links: [] };
      if (!row.links.some((link) => link.customer.displayName === drawn.to!.displayName)) row.links.push({ source, customer: drawn.to });
      rows.set(drawn.from.displayName, row);
    } else {
      others.push({ source, drawn });
    }
  }
  // A holding a row already shows needs no button of its own.
  const shown = new Set([...rows.values()].flatMap((row) => [row.supplier.displayName, ...row.links.map((link) => link.customer.displayName)]));
  const rest = others.filter(({ drawn }) => !drawn || !shown.has(drawn.from.displayName));
  const button = (source: AskSource, className: string, content: ReactNode, named: boolean) => (
    <button
      type="button"
      className={className}
      aria-label={named ? source.label : undefined}
      disabled={!canOpen(source)}
      title={canOpen(source) ? source.label : 'No Longer Available'}
      onClick={() => onOpen(source)}
    >
      {content}
    </button>
  );
  return (
    <ul className="ask-sources" aria-label="Sources">
      {[...rows.values()].map(({ supplier, links }) => (
        <li key={supplier.name} className="ask-row">
          <span className="ask-supplier">
            <Party party={supplier} />
            <span className="ask-supplier-name">{supplier.displayName}</span>
          </span>
          <FlowLink colour={SUPPLIED} />
          <span className="ask-customers">
            {links.map(({ source, customer }) => (
              <span key={source.id}>{button(source, 'ask-link', <Party party={customer} />, true)}</span>
            ))}
          </span>
        </li>
      ))}
      {rest.length > 0 && (
        <li className="ask-others">
          {rest.map(({ source, drawn }) => (
            <span key={`${source.kind}-${source.id}`}>
              {drawn
                ? button(source, 'ask-link', <Party party={drawn.from} />, true)
                : button(source, 'btn quiet small ask-source', source.label, false)}
            </span>
          ))}
        </li>
      )}
    </ul>
  );
}

/** Three squares with a pulse of light running across them, left to right, as along the graph's arrows. */
function Pulse() {
  return (
    <span className="ask-dots" aria-hidden="true">
      <i />
      <i />
      <i />
    </span>
  );
}
