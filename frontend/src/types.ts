export interface Rule {
  id?: string;
  description: string;
  spelExpression: string;
  active: boolean;
  updatedAt?: string;
}

export interface RuleStats {
  ruleId: string;
  matched: number;
  unmatched: number;
  errored: number;
}

export interface AnalyticsStats {
  totalMessages: number;
  totalEvaluations: number;
  ruleStats: RuleStats[];
  avgParseTimeNano: number;
  avgEvalTimeNano: number;
  avgTotalTimeNano: number;
}

export interface AuditRecord {
  auditId: string;
  ruleId: string;
  auditType: 'MATCHED' | 'UNMATCHED' | 'ERRORED';
  reason?: string;
  sourceEvent: string;
  routedEvent?: string;
  sourceTopic: string;
  partition: number;
  offset: number;
  timestamp: string;
  parseTimeNano: number;
  evalTimeNano: number;
  totalTimeNano: number;
}
