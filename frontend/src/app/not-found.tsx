import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[40vh] gap-4">
      <h2 className="text-2xl font-bold">404</h2>
      <p className="text-gray-500">페이지를 찾을 수 없습니다.</p>
      <Link href="/" className="text-blue-600 hover:underline text-sm">
        홈으로 돌아가기
      </Link>
    </div>
  );
}
