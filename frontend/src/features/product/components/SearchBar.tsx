'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

export function SearchBar() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [value, setValue] = useState(searchParams.get('search') ?? '');
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const navigate = (keyword: string) => {
    const params = new URLSearchParams(searchParams.toString());
    keyword.trim() ? params.set('search', keyword.trim()) : params.delete('search');
    params.delete('page');
    router.push(`?${params.toString()}`);
  };

  const handleChange = (newValue: string) => {
    setValue(newValue);
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => navigate(newValue), 400);
  };

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  return (
    <div className="relative max-w-md">
      <input
        type="text"
        value={value}
        onChange={(e) => handleChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            if (timerRef.current) clearTimeout(timerRef.current);
            navigate(value);
          }
        }}
        placeholder="상품명, 카테고리, 브랜드 검색..."
        className="w-full px-4 py-2.5 pr-10 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white"
      />
      {value && (
        <button
          onClick={() => handleChange('')}
          aria-label="검색어 초기화"
          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 text-lg leading-none"
        >
          ×
        </button>
      )}
    </div>
  );
}