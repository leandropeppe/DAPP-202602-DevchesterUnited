import '../styles/maradona-loader.css';

function MaradonaLoader() {
    return (
        <div
            className="football-valuation-loader"
            role="status"
            aria-live="polite"
        >
            <div
                className="football-valuation-loader__sprite"
                aria-hidden="true"
            />

            <h3 className="football-valuation-loader__title">
                Calculando cotización...
            </h3>

            <p className="football-valuation-loader__description">
                Aplicando estadísticas simuladas y estrategia de valorización.
            </p>
        </div>
    );
}

export function remainingLoadingTime(elapsedMs, minimumMs = 3000) {
    return Math.max(0, minimumMs - elapsedMs)
}

export default MaradonaLoader;