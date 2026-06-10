'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { useRecommendationStream } from '@/features/recommendation/hooks/use-recommendation-stream';
import { useRecommendationHistory } from '@/features/recommendation/hooks/use-recommendation-history';
import type { RecommendationResult } from '@/features/recommendation/types/recommendation.types';

export default function RecommendationsPage() {
  const [input, setInput] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');

  const { results, isComplete, isSearching, start } = useRecommendationStream();
  const { data: history, isLoading: historyLoading } = useRecommendationHistory();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const q = input.trim();
    if (!q) return;
    setSubmittedQuery(q);
    start(q);
  };

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl font-bold mb-6">AI 추천</h1>

      {/* 검색 입력 */}
      <form onSubmit={handleSubmit} className="flex gap-3 mb-8">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="원하는 상품을 자연어로 입력하세요 (예: 돌잔치 선물)"
          className="flex-1 border rounded-md px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          disabled={isSearching}
        />
        <Button type="submit" loading={isSearching} loadingText="검색 중...">
          AI 추천 받기
        </Button>
      </form>

      {/* 스트리밍 결과 */}
      {submittedQuery && (
        <section className="mb-10">
          <h2 className="text-base font-semibold text-gray-700 mb-3">
            &ldquo;{submittedQuery}&rdquo; 추천 결과
          </h2>

          {isSearching && results.length === 0 && (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <SkeletonCard key={i} />
              ))}
            </div>
          )}

          {results.length > 0 && (
            <div className="space-y-3">
              {results.map((r) => (
                <RecommendationCard key={r.productId} result={r} />
              ))}
              {isSearching && <SkeletonCard />}
            </div>
          )}

          {isComplete && results.length === 0 && (
            <p className="text-gray-500 py-8 text-center text-sm">추천 결과가 없습니다.</p>
          )}
        </section>
      )}

      {/* 히스토리 */}
      <section>
        <h2 className="text-base font-semibold text-gray-700 mb-3">추천 히스토리</h2>

        {historyLoading && (
          <div className="space-y-2">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-14 bg-gray-100 rounded-md animate-pulse" />
            ))}
          </div>
        )}

        {history && history.length === 0 && (
          <p className="text-gray-400 text-sm py-4">아직 추천 이력이 없습니다.</p>
        )}

        {history && history.length > 0 && (
          <div className="space-y-2">
            {history.map((item, idx) => (
              <div key={idx} className="flex items-center justify-between border rounded-md px-4 py-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{item.productName}</p>
                  <p className="text-xs text-gray-500 truncate mt-0.5">{item.reason}</p>
                </div>
                <div className="ml-4 text-right shrink-0">
                  <p className="text-xs text-gray-400">
                    {new Date(item.recommendedAt).toLocaleDateString('ko-KR')}
                  </p>
                  <p className="text-xs text-blue-500 font-medium">
                    {(item.score * 100).toFixed(1)}%
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function RecommendationCard({ result }: { result: RecommendationResult }) {
  return (
    <Link
      href={`/products/${result.productId}`}
      className="block border rounded-lg p-4 cursor-pointer hover:shadow-md transition-shadow"
    >
      <div className="flex justify-between items-start mb-1">
        <h3 className="font-semibold text-gray-900">{result.productName}</h3>
        <span className="text-xs text-blue-600 font-medium ml-2 shrink-0">
          {(result.score * 100).toFixed(1)}%
        </span>
      </div>
      <p className="text-sm text-gray-600 mb-2">{result.reason}</p>
      {result.conditions.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {result.conditions.map((c, i) => (
            <span key={i} className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full">
              {c}
            </span>
          ))}
        </div>
      )}
    </Link>
  );
}

function SkeletonCard() {
  return (
    <div className="border rounded-lg p-4 animate-pulse space-y-2">
      <div className="flex justify-between">
        <div className="h-4 bg-gray-200 rounded w-1/3" />
        <div className="h-4 bg-gray-200 rounded w-10" />
      </div>
      <div className="h-3 bg-gray-100 rounded w-3/4" />
      <div className="flex gap-2">
        <div className="h-5 bg-gray-100 rounded-full w-16" />
        <div className="h-5 bg-gray-100 rounded-full w-14" />
      </div>
    </div>
  );
}