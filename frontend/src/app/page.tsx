import Link from 'next/link';

export default function HomePage() {
  return (
    <section className="flex flex-col items-center justify-center min-h-[60vh] gap-6 text-center">
      <h1 className="text-4xl font-bold">AI가 찾아주는 최적의 상품</h1>
      <p className="text-gray-500 text-lg max-w-xl">
        AgentCart는 AI Agent가 사용자의 의도를 분석하고, 조건에 맞는 상품을 추천해 드립니다.
      </p>
      <div className="flex gap-4">
        <Link
          href="/recommendations"
          className="bg-blue-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-blue-700"
        >
          AI 추천 받기
        </Link>
        <Link
          href="/products"
          className="border border-gray-300 px-6 py-3 rounded-lg font-medium hover:bg-gray-100"
        >
          상품 둘러보기
        </Link>
      </div>
    </section>
  );
}
