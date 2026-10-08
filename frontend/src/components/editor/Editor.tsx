import { type Editor as TiptapEditor, EditorContent, Extension, InputRule, useEditor } from '@tiptap/react'
import Image from '@tiptap/extension-image'
import { Markdown } from '@tiptap/markdown'
import StarterKit from '@tiptap/starter-kit'
import { type ChangeEvent, useEffect, useRef, useState } from 'react'
import { uploadFile } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { UploadedImage } from '../../api/types'
import { MARKDOWN_LINK, isSafeLinkUrl, looksLikeMarkdown } from './markdown'

/**
 * `[글자](https://...)`를 치면 링크로 바꾼다 (T035a). Tiptap 기본 입력 규칙에는 링크가 없어 직접 만든다.
 * 주소가 http/https가 아니면 바꾸지 않고 글자 그대로 둔다.
 */
const MarkdownLinkInput = Extension.create({
  name: 'markdownLinkInput',
  addInputRules() {
    return [
      new InputRule({
        find: MARKDOWN_LINK,
        handler: ({ state, range, match }) => {
          const [, text, href] = match
          const link = state.schema.marks.link
          if (!link || !isSafeLinkUrl(href)) {
            return null
          }
          state.tr.replaceWith(range.from, range.to, state.schema.text(text, [link.create({ href })]))
            // 링크 뒤에 이어 치는 글자는 링크가 아니게
            .removeStoredMark(link)
        },
      }),
    ]
  },
})

/**
 * 글 본문 에디터 (POST-01, Tiptap). 서버 정화 허용 목록(HtmlSanitizer)에 있는 서식만 켠다:
 * 문단 제목, 굵게·기울임, 목록, 인용, 코드 블록, http/https 링크, 직접 올린 이미지(/uploads/..., T037).
 * 밑줄·취소선·구분선은 서버가 지우는 태그라 끈다. 저장할 때는 서버가 한 번 더 정화한다.
 *
 * 마크다운 입력 (T035a): 같은 에디터에서 `## `, `**굵게**`, `- `, `> `, ``` 같은 문법을 치면 바로 서식이 되고
 * (StarterKit의 입력 규칙과 MarkdownLinkInput), 마크다운 글을 붙여넣으면 서식으로 바꿔 넣는다(Markdown 확장).
 * 저장은 그대로 HTML이다.
 */
const editorExtensions = [
  StarterKit.configure({
    heading: { levels: [2, 3] },
    strike: false,
    underline: false,
    horizontalRule: false,
    link: { openOnClick: false, autolink: true, protocols: ['http', 'https'], defaultProtocol: 'https' },
  }),
  Markdown,
  MarkdownLinkInput,
  // 서버 허용 목록과 같게 src·alt만 쓴다. base64 이미지(data:)는 서버가 지우므로 받지 않는다
  Image.configure({ inline: false, allowBase64: false }),
]

export default function Editor({ initialHtml, onChange }: {
  /** 수정 화면에서 불러온 본문. 처음 그릴 때와 불러오기가 끝났을 때 한 번 넣는다 */
  initialHtml: string
  onChange: (html: string) => void
}) {
  // 붙여넣기 처리기는 에디터를 만들 때 정해지므로, 만들어진 에디터는 ref로 꺼내 쓴다
  const editorRef = useRef<TiptapEditor | null>(null)
  const editor = useEditor({
    extensions: editorExtensions,
    content: initialHtml,
    contentType: 'html',
    // 버튼의 켜짐 표시(굵게 등)를 커서가 움직일 때마다 다시 그린다
    shouldRerenderOnTransaction: true,
    onUpdate: ({ editor: current }) => onChange(current.getHTML()),
    editorProps: {
      handlePaste: (_view, event) => {
        const clipboard = event.clipboardData
        const text = clipboard?.getData('text/plain') ?? ''
        // 웹 페이지에서 복사한 서식 있는 글(text/html)은 Tiptap 기본 붙여넣기에 맡긴다
        if (!editorRef.current || clipboard?.getData('text/html') || !looksLikeMarkdown(text)) {
          return false
        }
        editorRef.current.commands.insertContent(text, { contentType: 'markdown' })
        return true
      },
    },
  })

  useEffect(() => {
    editorRef.current = editor
  }, [editor])

  useEffect(() => {
    if (editor && initialHtml !== editor.getHTML()) {
      editor.commands.setContent(initialHtml, { emitUpdate: false, contentType: 'html' })
    }
    // 불러온 본문(initialHtml)이 바뀔 때만 넣는다. 입력할 때마다 넣으면 커서가 맨 끝으로 튄다
  }, [editor, initialHtml])

  return (
    <div>
      <Toolbar editor={editor} />
      <EditorContent editor={editor} className="editor-area" />
      <p className="hint">
        마크다운 문법도 쓸 수 있습니다: <code>## 제목</code> <code>**굵게**</code> <code>*기울임*</code>{' '}
        <code>- 목록</code> <code>1. 번호</code> <code>&gt; 인용</code> <code>```</code> 코드{' '}
        <code>[글자](https://주소)</code>. 마크다운 글을 붙여넣어도 서식으로 바뀝니다.
      </p>
    </div>
  )
}

function Toolbar({ editor }: { editor: TiptapEditor | null }) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)

  /**
   * 고른 사진을 고른 순서대로 하나씩 올리고, 올라간 것부터 커서 자리에 넣는다 (T037).
   * 하나가 거절돼도(형식·크기) 본문은 그대로이고 이유를 알린다(spec US2 시나리오 6).
   */
  async function insertImages(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (!editor || files.length === 0) {
      return
    }
    setUploading(true)
    setUploadError(null)
    for (const file of files) {
      try {
        const image = await uploadFile<UploadedImage>('/api/images', file)
        // 방금 넣은 사진이 선택된 채라 setImage는 그 사진을 바꿔 버린다. 선택의 끝 뒤에 넣어 고른 순서를 지킨다
        editor.chain().focus().insertContentAt(editor.state.selection.to,
          { type: 'image', attrs: { src: image.url, alt: file.name } }).run()
      } catch (error) {
        setUploadError(`${file.name}: ${errorMessage(error)}`)
      }
    }
    setUploading(false)
  }

  if (!editor) {
    return <div className="editor-bar" />
  }
  const chain = () => editor.chain().focus()
  const button = (label: string, active: boolean, run: () => void) => (
    <button key={label} type="button" className={active ? 'btn on' : 'btn'} aria-pressed={active}
            onMouseDown={(event) => event.preventDefault()} onClick={run}>
      {label}
    </button>
  )

  function toggleLink() {
    if (editor!.isActive('link')) {
      chain().unsetLink().run()
      return
    }
    const url = window.prompt('링크 주소 (http:// 또는 https://)')
    if (url && isSafeLinkUrl(url.trim())) {
      chain().setLink({ href: url.trim() }).run()
    } else if (url) {
      window.alert('http:// 또는 https://로 시작하는 주소만 넣을 수 있습니다.')
    }
  }

  return (
    <div className="editor-bar" role="toolbar" aria-label="서식">
      {button('제목', editor.isActive('heading', { level: 2 }), () => chain().toggleHeading({ level: 2 }).run())}
      {button('소제목', editor.isActive('heading', { level: 3 }), () => chain().toggleHeading({ level: 3 }).run())}
      {button('굵게', editor.isActive('bold'), () => chain().toggleBold().run())}
      {button('기울임', editor.isActive('italic'), () => chain().toggleItalic().run())}
      {button('목록', editor.isActive('bulletList'), () => chain().toggleBulletList().run())}
      {button('번호 목록', editor.isActive('orderedList'), () => chain().toggleOrderedList().run())}
      {button('인용', editor.isActive('blockquote'), () => chain().toggleBlockquote().run())}
      {button('코드', editor.isActive('codeBlock'), () => chain().toggleCodeBlock().run())}
      {button('링크', editor.isActive('link'), toggleLink)}
      <button type="button" className="btn" disabled={uploading}
              onMouseDown={(event) => event.preventDefault()} onClick={() => fileInput.current?.click()}>
        {uploading ? '올리는 중…' : '이미지'}
      </button>
      <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple hidden
             onChange={insertImages} />
      {uploadError && <span className="err" role="alert">{uploadError}</span>}
    </div>
  )
}
