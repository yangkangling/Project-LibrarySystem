const EDGE_SIZE = 72
const MAX_SPEED = 28
const MOVE_THRESHOLD = 4

let state = null
let frameId = 0
let installed = false

// 在表格内拖拽选中文字时，鼠标靠近左右边缘自动横向滚动。
export function installTableAutoScroll() {
  if (installed || typeof document === 'undefined') {
    return () => {}
  }
  installed = true
  document.addEventListener('mousedown', handleMouseDown, true)
  return () => {
    installed = false
    stopAutoScroll()
    document.removeEventListener('mousedown', handleMouseDown, true)
  }
}

function handleMouseDown(event) {
  if (event.button !== 0 || isInteractiveElement(event.target)) {
    return
  }
  const scroller = findTableScroller(event.target)
  if (!scroller) {
    return
  }
  state = {
    scroller,
    startX: event.clientX,
    startY: event.clientY,
    clientX: event.clientX,
    dragging: false
  }
  document.addEventListener('mousemove', handleMouseMove, true)
  document.addEventListener('mouseup', handleMouseUp, true)
}

function handleMouseMove(event) {
  if (!state || event.buttons === 0) {
    stopAutoScroll()
    return
  }
  state.clientX = event.clientX
  if (!state.dragging) {
    const movedX = Math.abs(event.clientX - state.startX)
    const movedY = Math.abs(event.clientY - state.startY)
    state.dragging = movedX > MOVE_THRESHOLD || movedY > MOVE_THRESHOLD
  }
  if (state.dragging) {
    startLoop()
  }
}

function handleMouseUp() {
  stopAutoScroll()
}

function startLoop() {
  if (!frameId) {
    frameId = requestAnimationFrame(scrollFrame)
  }
}

function scrollFrame() {
  frameId = 0
  if (!state) {
    return
  }
  const { scroller, clientX } = state
  const rect = scroller.getBoundingClientRect()
  let speed = 0

  if (clientX > rect.right - EDGE_SIZE) {
    speed = scrollSpeed(clientX - (rect.right - EDGE_SIZE))
  } else if (clientX < rect.left + EDGE_SIZE) {
    speed = -scrollSpeed((rect.left + EDGE_SIZE) - clientX)
  }

  if (speed !== 0) {
    scroller.scrollLeft += speed
  }
  frameId = requestAnimationFrame(scrollFrame)
}

function scrollSpeed(distance) {
  return Math.min(MAX_SPEED, Math.max(4, Math.ceil(distance / 3)))
}

function stopAutoScroll() {
  if (frameId) {
    cancelAnimationFrame(frameId)
    frameId = 0
  }
  state = null
  document.removeEventListener('mousemove', handleMouseMove, true)
  document.removeEventListener('mouseup', handleMouseUp, true)
}

function findTableScroller(target) {
  const element = target instanceof Element ? target : null
  const table = element?.closest('.el-table')
  if (!table) {
    return null
  }
  const candidates = [
    table.querySelector('.el-table__body-wrapper .el-scrollbar__wrap'),
    table.querySelector('.el-table__body-wrapper'),
    table.querySelector('.el-scrollbar__wrap')
  ].filter(Boolean)
  return candidates.find((item) => item.scrollWidth > item.clientWidth)
}

function isInteractiveElement(target) {
  const element = target instanceof Element ? target : null
  return Boolean(element?.closest('button, input, textarea, select, a, .el-button, .el-checkbox, .el-input, .el-select, .el-radio'))
}
