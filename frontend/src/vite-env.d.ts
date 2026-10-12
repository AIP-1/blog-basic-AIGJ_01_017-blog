/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 블로그 주소는 같은 출처의 /@주소 경로라(스텝 21) 플랫폼 주소 설정이 따로 없다 */
  readonly MODE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
