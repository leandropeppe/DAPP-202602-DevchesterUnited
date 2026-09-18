export default function StatusMessage ({ type, children }) {
  if (!children) return null

  const role = type === 'error' ? 'alert' : 'status'
  return <p className={`status-message status-message--${type}`} role={role} aria-live="polite">{children}</p>
}
