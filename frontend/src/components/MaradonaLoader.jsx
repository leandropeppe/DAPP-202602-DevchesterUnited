export const MIN_LOADING_MS = 2000

export function remainingLoadingTime (startedAt, now = Date.now()) {
  return Math.max(0, MIN_LOADING_MS - (now - startedAt))
}

export default function MaradonaLoader () {
  return (
    <div className="maradona-loader" role="status" aria-live="polite">
      <img
        className="maradona-loader__image"
        src="/images/maradona-loader.png"
        alt=""
        aria-hidden="true"
        onError={(event) => { event.currentTarget.hidden = true }}
      />
      <span>Calculando simulación…</span>
    </div>
  )
}
