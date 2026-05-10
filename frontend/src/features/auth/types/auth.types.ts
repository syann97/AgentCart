import { z } from 'zod';

export const loginSchema = z.object({
  email: z.string().email('올바른 이메일을 입력해주세요'),
  password: z.string().min(8, '비밀번호는 8자 이상이어야 합니다'),
});

export const registerSchema = z
  .object({
    email: z.string()
      .min(1, '이메일을 입력해주세요')
      .email('올바른 이메일 형식이 아닙니다'),
    password: z.string()
      .min(1, '비밀번호를 입력해주세요')
      .min(8, '비밀번호는 8자 이상이어야 합니다'),
    passwordConfirm: z.string(),
    name: z.string().min(1, '이름을 입력해주세요'),
    nickname: z.string()
      .min(1, '닉네임을 입력해주세요')
      .min(2, '닉네임은 2자 이상 20자 이하여야 합니다')
      .max(20, '닉네임은 2자 이상 20자 이하여야 합니다'),
  })
  .refine((data) => data.password === data.passwordConfirm, {
    message: '비밀번호가 일치하지 않습니다',
    path: ['passwordConfirm'],
  });

export type LoginFormValues = z.infer<typeof loginSchema>;
export type RegisterFormValues = z.infer<typeof registerSchema>;

export interface MemberInfo {
  id: number;
  email: string;
  name: string;
  nickname: string;
  role: 'MEMBER' | 'ADMIN';
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface LoginResponseData {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  member: MemberInfo;
}

export interface RegisterResponseData {
  id: number;
  email: string;
  name: string;
  nickname: string;
  role: string;
}