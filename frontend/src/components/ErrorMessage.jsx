export default function ErrorMessage({ message, onRetry }) {
  if (!message) return null
  return <div className="error-message" role="alert"><p>{message}</p>{onRetry && <button type="button" className="button button-small button-outline" onClick={onRetry}>다시 시도</button>}</div>
}
