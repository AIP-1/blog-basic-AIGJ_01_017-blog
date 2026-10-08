import { type Editor as TiptapEditor, EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { useEffect } from 'react'

/**
 * 글 본문 에디터 (POST-01, Tiptap). 서버 정화 허용 목록(HtmlSanitizer)에 있는 서식만 켠다:
 * 문단 제목, 굵게·기울임, 목록, 인용, 코드 블록, http/https 링크. 이미지는 스텝 7(POST-05).
 * 밑줄·취소선·구분선은 서버가 지우는 태그라 끈다. 저장할 때는 서버가 한 번 더 정화한다.
 */
const editorExtensions = [
  StarterKit.configure({
    heading: { levels: [2, 3] },
    strike: false,
    underline: false,
    horizontalRule: false,
    link: { openOnClick: false, autolink: true, protocols: ['http', 'https'], defaultProtocol: 'https' },
  }),
]

export default function Editor({ initialHtml, onChange }: {
  /** 수정 화면에서 불러온 본문. 처음 그릴 때와 불러오기가 끝났을 때 한 번 넣는다 */
  initialHtml: string
  onChange: (html: string) => void
}) {
  const editor = useEditor({
    extensions: editorExtensions,
    content: initialHtml,
    // 버튼의 켜짐 표시(굵게 등)를 커서가 움직일 때마다 다시 그린다
    shouldRerenderOnTransaction: true,
    onUpdate: ({ editor: current }) => onChange(current.getHTML()),
  })

  useEffect(() => {
    if (editor && initialHtml !== editor.getHTML()) {
      editor.commands.setContent(initialHtml, { emitUpdate: false })
    }
    // 불러온 본문(initialHtml)이 바뀔 때만 넣는다. 입력할 때마다 넣으면 커서가 맨 끝으로 튄다
  }, [editor, initialHtml])

  return (
    <div>
      <Toolbar editor={editor} />
      <EditorContent editor={editor} className="editor-area" />
    </div>
  )
}

function Toolbar({ editor }: { editor: TiptapEditor | null }) {
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
    if (url && /^https?:\/\/\S+$/i.test(url.trim())) {
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
      <button type="button" className="btn" disabled title="이미지는 스텝 7에서 넣을 수 있습니다">이미지</button>
    </div>
  )
}
