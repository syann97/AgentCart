export interface RecommendationResult {
  productId: number;
  productName: string;
  price: number;
  reason: string;
  conditions: string[];
  score: number;
}

export type RecommendationStreamOutcome =
  | 'SUCCESS'
  | 'NO_RESULTS'
  | 'OUT_OF_SCOPE'
  | 'CLARIFICATION_REQUIRED'
  | 'FALLBACK';

export type RecommendationStreamEvent =
  | {
      type: 'status';
      data: {
        phase: 'SEARCHING' | 'RESEARCHING' | 'FALLBACK_SEARCHING';
        searchAttempt: number;
        message: string;
      };
    }
  | { type: 'result'; data: RecommendationResult }
  | {
      type: 'done';
      data: {
        requestId: string;
        outcome: RecommendationStreamOutcome;
        actionCode: string;
        message: string;
        resultCount: number;
      };
    }
  | {
      type: 'error';
      data: {
        requestId: string | null;
        code: string;
        message: string;
        retryable: boolean;
      };
    };

export interface RecommendationStreamError {
  code: string;
  message: string;
  retryable: boolean;
  transport: boolean;
}

export interface RecommendationHistoryItem {
  productId: number;
  productName: string;
  reason: string;
  score: number;
  recommendedAt: string;
}
