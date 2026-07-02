import { THREAT_TOOLTIP } from '@/lib/insightLabels'

export interface HelpSection {
  id: string
  title: string
  paragraphs?: string[]
  bullets?: string[]
  image?: { src: string; alt: string; caption?: string }
  link?: { href: string; label: string; external?: boolean }
}

export const HELP_SECTIONS: HelpSection[] = [
  {
    id: 'what',
    title: 'What is Aegis?',
    paragraphs: [
      'Aegis is a live competitor intelligence platform: scheduled harvesters collect public signals, a three-stage AI pipeline filters noise and scores strategic threat, and this dashboard streams results in real time.',
      'You can run the full stack yourself (Docker, Render + Neon) for other competitors and environments — see the GitHub repo. This public site is one hosted instance with a few limits (below).',
    ],
  },
  {
    id: 'hosted',
    title: 'This public deployment',
    paragraphs: [
      'This URL tracks Google, Amazon, and OpenAI. The API may take up to a minute to wake after idle periods on free-tier hosting.',
      'Ask Agent can use the server OpenAI key for a limited trial (see Settings). RAG vector search is disabled on this deployment to save database cost — answers use the selected article only.',
    ],
    bullets: [
      'Open Settings if you see “Add API Key” or the hosted trial has expired.',
      'Full RAG (cited sources, related stories) is available in the GitHub repo or on request.',
    ],
  },
  {
    id: 'dashboard',
    title: 'Using the dashboard',
    paragraphs: [
      'The main feed is paginated — use Load more for older insights. Sidebar filters apply server-side (competitor, threat, dates, search).',
      'Pipeline stats at the top reflect database totals; header “in feed” counts only what you have loaded in this session.',
    ],
    image: {
      src: '/help/dashboard.png',
      alt: 'Dashboard with filters, stats, and threat cards',
      caption: 'Filters and pipeline stats on the main feed.',
    },
  },
  {
    id: 'threat',
    title: 'Threat scores',
    paragraphs: [THREAT_TOOLTIP],
    bullets: [
      'Colored left border on each card = competitor.',
      'High threat filter matches scores ≥ 7.',
    ],
  },
  {
    id: 'ask-agent',
    title: 'Ask Agent',
    paragraphs: [
      'Open Ask Agent on any threat card to ask a strategic question about that article. Prior questions for the same article appear in the panel and restore full answers when selected.',
    ],
    image: {
      src: '/help/ask-agent.png',
      alt: 'Ask Agent panel with answer and sources',
      caption: 'Screenshot from a RAG-enabled local run; this public deployment answers from the current article only.',
    },
  },
  {
    id: 'competitor',
    title: 'Competitor pages',
    paragraphs: [
      'Click a competitor name on a card to open /competitor/:name — category and source mix plus a threat-sorted insight list.',
    ],
    image: {
      src: '/help/competitor.png',
      alt: 'Competitor drill-down page',
      caption: 'Per-competitor summary and feed.',
    },
  },
  {
    id: 'caveats',
    title: 'Read, star, and similar stories',
    bullets: [
      'Read, star, and dismiss are stored in this browser only — not synced across devices.',
      'Unread filter applies to the loaded feed, not the full database.',
      'Similar headlines in the feed are title-token clusters — not the same as RAG “related stories” (disabled on this public deployment).',
    ],
  },
  {
    id: 'more',
    title: 'Source code',
    paragraphs: ['Architecture, API, and deployment details live in the repository README.'],
    link: {
      href: 'https://github.com/vishtechie07/aegis-marketing-intelligence-dashboard',
      label: 'View on GitHub',
      external: true,
    },
  },
]
