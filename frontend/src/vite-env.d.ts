/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 플랫폼 주소. 개발 blog.test, 운영 blog.com */
  readonly VITE_PLATFORM_DOMAIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
