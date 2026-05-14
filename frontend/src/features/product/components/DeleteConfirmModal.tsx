import { Button } from '@/components/ui/Button';

interface DeleteConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  isPending: boolean;
}

export function DeleteConfirmModal({ isOpen, onClose, onConfirm, isPending }: DeleteConfirmModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 max-w-sm w-full mx-4">
        <h2 className="text-lg font-semibold mb-2">상품 삭제</h2>
        <p className="text-gray-600 mb-6">정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.</p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={isPending}>
            취소
          </Button>
          <button
            onClick={onConfirm}
            disabled={isPending}
            className="rounded-md py-2 px-4 text-sm font-medium bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
          >
            {isPending ? '삭제 중...' : '삭제'}
          </button>
        </div>
      </div>
    </div>
  );
}