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
}
