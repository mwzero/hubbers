import { useMemo } from 'react';

interface MarkdownContentProps {
  content: string;
}

/**
 * Lightweight markdown-like renderer for chat messages.
 * Handles code blocks (```lang\n...\n```), inline code (`...`),
 * bold (**...**), and preserves whitespace/newlines.
 */
export function MarkdownContent({ content }: MarkdownContentProps) {
  const parts = useMemo(() => parseContent(content), [content]);

  return (
    <div className="text-sm leading-relaxed space-y-2">
      {parts.map((part, i) => {
        if (part.type === 'code') {
          return (
            <div key={i} className="relative group">
              {part.lang && (
                <div className="absolute top-0 right-0 px-2 py-0.5 text-[9px] text-muted-foreground bg-muted rounded-bl font-mono">
                  {part.lang}
                </div>
              )}
              <pre className="p-3 rounded-lg bg-zinc-950 text-zinc-100 text-xs font-mono whitespace-pre overflow-x-auto">
                <code>{part.content}</code>
              </pre>
              <button
                className="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition-opacity text-[10px] px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-300 hover:text-white"
                onClick={() => navigator.clipboard.writeText(part.content)}
              >
                Copy
              </button>
            </div>
          );
        }

        // Text block — handle inline formatting
        return (
          <p key={i} className="whitespace-pre-wrap break-words">
            <InlineFormatted text={part.content} />
          </p>
        );
      })}
    </div>
  );
}

function InlineFormatted({ text }: { text: string }) {
  // Split on inline code and bold
  const segments = useMemo(() => parseInline(text), [text]);
  return (
    <>
      {segments.map((seg, i) => {
        if (seg.type === 'inlineCode') {
          return (
            <code key={i} className="px-1.5 py-0.5 rounded bg-muted font-mono text-xs text-foreground">
              {seg.content}
            </code>
          );
        }
        if (seg.type === 'bold') {
          return <strong key={i}>{seg.content}</strong>;
        }
        return <span key={i}>{seg.content}</span>;
      })}
    </>
  );
}

interface ContentPart {
  type: 'text' | 'code';
  content: string;
  lang?: string;
}

function parseContent(raw: string): ContentPart[] {
  const parts: ContentPart[] = [];
  const codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
  let lastIndex = 0;
  let match;

  while ((match = codeBlockRegex.exec(raw)) !== null) {
    if (match.index > lastIndex) {
      const text = raw.slice(lastIndex, match.index).trim();
      if (text) parts.push({ type: 'text', content: text });
    }
    parts.push({ type: 'code', content: match[2].trimEnd(), lang: match[1] || undefined });
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < raw.length) {
    const text = raw.slice(lastIndex).trim();
    if (text) parts.push({ type: 'text', content: text });
  }

  if (parts.length === 0) {
    parts.push({ type: 'text', content: raw });
  }

  return parts;
}

interface InlineSegment {
  type: 'text' | 'inlineCode' | 'bold';
  content: string;
}

function parseInline(text: string): InlineSegment[] {
  const segments: InlineSegment[] = [];
  // Match inline code or bold
  const regex = /(`[^`]+`|\*\*[^*]+\*\*)/g;
  let lastIndex = 0;
  let match;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', content: text.slice(lastIndex, match.index) });
    }
    const token = match[0];
    if (token.startsWith('`')) {
      segments.push({ type: 'inlineCode', content: token.slice(1, -1) });
    } else if (token.startsWith('**')) {
      segments.push({ type: 'bold', content: token.slice(2, -2) });
    }
    lastIndex = match.index + token.length;
  }

  if (lastIndex < text.length) {
    segments.push({ type: 'text', content: text.slice(lastIndex) });
  }

  return segments;
}
