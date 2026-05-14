import type { ProductSummary } from '../types/product.types';
import { ProductCard } from './ProductCard';

interface ProductListProps {
  products: ProductSummary[];
}

export function ProductList({ products }: ProductListProps) {
  if (products.length === 0) {
    return (
      <div className="text-center py-16 text-gray-500">
        <p>상품이 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
      {products.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  );
}
