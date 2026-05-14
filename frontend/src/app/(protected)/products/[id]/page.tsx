'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { useProduct } from '@/features/product/hooks/use-product';
import { useDeleteProduct } from '@/features/product/hooks/use-delete-product';
import { ProductDetail } from '@/features/product/components/ProductDetail';
import { DeleteConfirmModal } from '@/features/product/components/DeleteConfirmModal';
import { useAuthStore } from '@/stores/auth.store';

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const router = useRouter();
  const member = useAuthStore((s) => s.member);
  const isAdmin = member?.role === 'ADMIN';
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  const { data, isLoading, isError, error } = useProduct(id);
  const { mutate: deleteProduct, isPending: isDeleting } = useDeleteProduct({
    onSuccess: () => router.push('/products'),
  });

  const is404 = (error as { response?: { status?: number } })?.response?.status === 404;

  return (
    <div>
      {isLoading && (
        <div className="max-w-2xl space-y-4">
          <div className="h-8 bg-gray-100 rounded animate-pulse w-2/3" />
          <div className="h-4 bg-gray-100 rounded animate-pulse w-1/3" />
          <div className="h-24 bg-gray-100 rounded animate-pulse" />
          <div className="h-8 bg-gray-100 rounded animate-pulse w-1/4" />
        </div>
      )}

      {isError && (
        <p className="text-red-500 py-8 text-center">
          {is404 ? '존재하지 않는 상품입니다.' : '상품 정보를 불러오지 못했습니다.'}
        </p>
      )}

      {data && (
        <>
          {isAdmin && (
            <div className="flex gap-2 mb-4">
              <Link
                href={`/products/${id}/edit`}
                className="px-4 py-2 border text-sm rounded-md text-gray-700 hover:bg-gray-50"
              >
                수정
              </Link>
              <button
                onClick={() => setShowDeleteModal(true)}
                className="px-4 py-2 text-sm rounded-md bg-red-600 text-white hover:bg-red-700"
              >
                삭제
              </button>
            </div>
          )}
          <ProductDetail product={data.data} />
        </>
      )}

      <DeleteConfirmModal
        isOpen={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        onConfirm={() => deleteProduct(id)}
        isPending={isDeleting}
      />
    </div>
  );
}