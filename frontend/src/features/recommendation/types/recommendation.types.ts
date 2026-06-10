export interface RecommendationResult {
  productId: number;
  productName: string;
  price: number;
  reason: string;
  conditions: string[];
  score: number;
}

export interface RecommendationStreamChunk {
  type: 'partial' | 'complete' | 'error';
  data: Partial<RecommendationResult>;
}

export interface RecommendationHistoryItem {
  productId: number;
  productName: string;
  reason: string;
  score: number;
  recommendedAt: string;
}
