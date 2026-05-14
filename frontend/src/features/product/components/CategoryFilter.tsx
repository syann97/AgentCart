'use client';

import { useRouter, useSearchParams } from 'next/navigation';

const CATEGORIES = ['electronics', 'clothing', 'food', 'books', 'sports'];

export function CategoryFilter() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const current = searchParams.get('category');

  const select = (category: string | null) => {
    const params = new URLSearchParams(searchParams.toString());
    if (category) {
      params.set('category', category);
    } else {
      params.delete('category');
    }
    params.delete('page');
    router.push(`?${params.toString()}`);
  };

  return (
    <div className="flex flex-wrap gap-2">
      <button
        onClick={() => select(null)}
        className={`px-3 py-1.5 rounded-full text-sm transition-colors ${
          !current ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
        }`}
      >
        전체
      </button>
      {CATEGORIES.map((cat) => (
        <button
          key={cat}
          onClick={() => select(cat)}
          className={`px-3 py-1.5 rounded-full text-sm transition-colors ${
            current === cat ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          {cat}
        </button>
      ))}
    </div>
  );
}
