function amount (value) {
    if (value === null || value === undefined) {
        return '—'
    }

    return Number(value).toLocaleString('es-AR', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    })
}

function operationLabel (operationType) {
    if (operationType === 'PURCHASE') {
        return 'Compra'
    }

    if (operationType === 'SALE') {
        return 'Venta'
    }

    const operationSuffix = operationType
        ? `: ${operationType}`
        : ''

    return `Tipo de operación no reconocido${operationSuffix}`
}

function transactionPlayerName (transaction) {
    const name =
        transaction?.player_name ??
        transaction?.player_name_snapshot

    return typeof name === 'string' && name.trim()
        ? name.trim()
        : 'Jugador sin nombre disponible'
}

function transactionDate (value) {
    if (!value) {
        return 'Fecha no disponible'
    }

    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
        return 'Fecha no disponible'
    }

    return new Intl.DateTimeFormat('es-AR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    }).format(date)
}

function operationClassName (operationType) {
    if (operationType === 'PURCHASE') {
        return 'market-transaction-card--purchase'
    }

    if (operationType === 'SALE') {
        return 'market-transaction-card--sale'
    }

    return ''
}

export default function MarketTransactionCard ({
                                                   transaction,
                                                   operationLabel: customOperationLabel
                                               }) {
    const operationClass =
        operationClassName(
            transaction.operation_type
        )

    return (
        <article
            className={`card market-transaction-card ${operationClass}`}
            data-player-id={transaction.player_id}
        >
            <div>
                <strong>
                    {customOperationLabel ?? operationLabel(
                        transaction.operation_type
                    )}
                </strong>

                <span>
          {transactionDate(
              transaction.confirmed_at
          )}
        </span>
            </div>

            <p className="market-transaction-card__snapshot">
                {transactionPlayerName(transaction)}
            </p>

            <dl>
                <div>
                    <dt>
                        Cantidad
                    </dt>

                    <dd>
                        {transaction.quantity}
                    </dd>
                </div>

                <div>
                    <dt>
                        Precio unitario
                    </dt>

                    <dd>
                        {amount(
                            transaction.unit_price
                        )}
                    </dd>
                </div>

                <div>
                    <dt>
                        Total
                    </dt>

                    <dd>
                        {amount(
                            transaction.total_amount
                        )}
                    </dd>
                </div>

                <div>
                    <dt>
                        Versión cotización
                    </dt>

                    <dd>
                        {transaction.quote_version}
                    </dd>
                </div>

                <div>
                    <dt>
                        P/L realizada
                    </dt>

                    <dd>
                        {amount(
                            transaction.realized_pnl
                        )}
                    </dd>
                </div>
            </dl>
        </article>
    )
}
