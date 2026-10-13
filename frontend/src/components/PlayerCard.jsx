import '../styles/PlayerCard.css'


const COUNTRY_CODES = {
    Argentina: 'AR',
    Austria: 'AT',
    Belgium: 'BE',
    Brazil: 'BR',
    Cameroon: 'CM',
    Canada: 'CA',
    Chile: 'CL',
    Colombia: 'CO',
    Croatia: 'HR',
    Czechia: 'CZ',
    'Czech Republic': 'CZ',
    Denmark: 'DK',
    Ecuador: 'EC',
    Egypt: 'EG',
    England: 'GB',
    Finland: 'FI',
    France: 'FR',
    Germany: 'DE',
    Ghana: 'GH',
    Greece: 'GR',
    Hungary: 'HU',
    Iceland: 'IS',
    Ireland: 'IE',
    Italy: 'IT',
    'Ivory Coast': 'CI',
    Japan: 'JP',
    Mexico: 'MX',
    Morocco: 'MA',
    Netherlands: 'NL',
    Nigeria: 'NG',
    Norway: 'NO',
    Paraguay: 'PY',
    Peru: 'PE',
    Poland: 'PL',
    Portugal: 'PT',
    Romania: 'RO',
    Scotland: 'GB',
    Senegal: 'SN',
    Serbia: 'RS',
    Slovakia: 'SK',
    Slovenia: 'SI',
    'South Korea': 'KR',
    Spain: 'ES',
    Sweden: 'SE',
    Switzerland: 'CH',
    Tunisia: 'TN',
    Turkey: 'TR',
    Ukraine: 'UA',
    'United States': 'US',
    Uruguay: 'UY',
    Venezuela: 'VE',
    Wales: 'GB'
}

const POSITION_THEMES = {
    Goalkeeper: 'green',
    Defender: 'red',
    Midfielder: 'blue',
    Forward: 'purple'
}

const POSITION_ABBREVIATIONS = {
    Goalkeeper: 'PO',
    Defender: 'DF',
    Midfielder: 'MC',
    Forward: 'DL'
}

function noInfo (value) {
    return value === null || value === undefined || value === ''
        ? 'Sin información'
        : value
}

function getPlayerTheme (player) {
    return POSITION_THEMES[player.position] ?? 'neutral'
}

function getPositionAbbreviation (position) {
    return POSITION_ABBREVIATIONS[position] ??
        position?.slice(0, 2).toUpperCase() ??
        '--'
}

function PlayerSilhouette () {
    return (
        <svg
            className="player-card__silhouette"
            viewBox="0 0 300 300"
            aria-hidden="true"
        >
            <path
                d="
          M150 42
          C119 42 100 66 102 96
          C103 118 115 136 128 145
          L125 161
          C102 168 76 180 63 204
          C53 222 47 249 44 275
          L256 275
          C253 249 247 222 237 204
          C224 180 198 168 175 161
          L172 145
          C185 136 197 118 198 96
          C200 66 181 42 150 42
          Z
        "
            />

            <path
                className="player-card__silhouette-highlight"
                d="
          M121 162
          C130 173 139 178 150 178
          C161 178 170 173 179 162
          L192 168
          C181 194 168 205 150 205
          C132 205 119 194 108 168
          Z
        "
            />
        </svg>
    )
}

function ShieldIcon () {
    return (
        <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
        >
            <path
                d="M12 2 20 5v6c0 5.3-3.4 9.3-8 11-4.6-1.7-8-5.7-8-11V5l8-3Z"
            />
            <path d="M8 9h8M9 13h6" />
        </svg>
    )
}

function TrophyIcon () {
    return (
        <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
        >
            <path
                d="
          M8 3h8v3h3v3
          c0 3-2 5-5 5h-1v3h4v2H7v-2h4v-3h-1
          c-3 0-5-2-5-5V6h3V3Z
        "
            />
        </svg>
    )
}

export default function PlayerCard ({ player, onOpen }) {
    const theme = getPlayerTheme(player)
    const countryCode = COUNTRY_CODES[player.nationality]

    function handleOpen () {
        onOpen?.(player)
    }

    function handleKeyDown (event) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault()
            handleOpen()
        }
    }

    return (
        <article
            className={`player-card player-card--${theme}`}
            tabIndex="0"
            role="link"
            onClick={handleOpen}
            onKeyDown={handleKeyDown}
            aria-label={`Ver estadísticas de ${noInfo(player.name)}`}
        >
            <div
                className="player-card__shine"
                aria-hidden="true"
            />

            <div className="player-card__rating">
                <strong>-</strong>

                <span>
          {getPositionAbbreviation(player.position)}
        </span>
            </div>

            <div
                className="player-card__portrait"
                aria-hidden="true"
            >
                <PlayerSilhouette />
            </div>

            <div className="player-card__content">
                <h3 className="player-card__name">
                    {noInfo(player.name)}
                </h3>

                <div className="player-card__metadata">
                    <div>
            <span className="player-card__icon player-card__icon--outline">
              <ShieldIcon />
            </span>

                        <span>
              {noInfo(player.team)}
            </span>
                    </div>

                    <div>
            <span className="player-card__icon">
              <TrophyIcon />
            </span>

                        <span>
              {noInfo(player.league)}
            </span>
                    </div>

                    <div>
                        {countryCode
                            ? (
                                <img
                                    src={`https://flagcdn.com/${countryCode.toLowerCase()}.svg`}
                                    alt=""
                                    className="player-card__flag"
                                />
                            )
                            : (
                                <span className="player-card__icon">
                  ◉
                </span>
                            )}

                        <span>
              {noInfo(player.nationality)}
            </span>
                    </div>
                </div>
            </div>

            <div
                className="player-card__action"
                aria-hidden="true"
            >
        <span>
          Ver estadísticas
        </span>

                <strong>
                    →
                </strong>
            </div>
        </article>
    )
}