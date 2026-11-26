/** Armenian labels for status enums coming from the API. */
export const STATUS_LABEL: Record<string, string> = {
  RUNNING: 'Ընթացքում', SUCCESS: 'Հաջող', PARTIAL: 'Մասնակի', FAILED: 'Ձախողված', UNAVAILABLE: 'Անհասանելի',
  PENDING: 'Սպասում է', APPROVED: 'Հաստատված', REJECTED: 'Մերժված', AUTO_APPROVED: 'Ավտոմատ հաստատված',
  CONFIRMED: 'Հաստատված', SUPERSEDED: 'Փոխարինված',
  COMPLETE: 'Ամբողջական', NOT_IMPORTED: 'Ներմուծված չէ', NOT_IMPLEMENTED: 'Միացված չէ',
  CRAWL: 'Քրոլինգ', CSV: 'CSV',
};

export const statusLabel = (s: string | null | undefined) => (s ? STATUS_LABEL[s] ?? s : '—');
