import '../styles/playerCard.css'

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

export function getCountryCode (nationality) {
    return COUNTRY_CODES[nationality]
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

const PLAYER_SILHOUETTES = {
    blue: '/images/player-silhouette-blue.png',
    red: '/images/player-silhouette-red.png',
    green: '/images/player-silhouette-green.png',
    purple: '/images/player-silhouette-purple.png'
}

const LEAGUE_LOGOS = {
    'Premier League': '/images/leagues/premier-league.png',
    LaLiga: '/images/leagues/la-liga.png',
    'Serie A': '/images/leagues/serie-a.png',
    Bundesliga: '/images/leagues/bundesliga.png',
    'Ligue 1': '/images/leagues/ligue-1.png'
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

function ShieldIcon () {
    return (
        <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
        >
            <path d="M12 2 20 5v6c0 5.3-3.4 9.3-8 11-4.6-1.7-8-5.7-8-11V5l8-3Z" />
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

export default function PlayerCard ({
                                        player,
                                        onOpen,
                                        interactive = true
                                    }) {
    const CardElement = interactive ? 'a' : 'article'
    const theme = getPlayerTheme(player)
    const countryCode = getCountryCode(player.nationality)
    const leagueLogo = LEAGUE_LOGOS[player.league]

    const silhouetteSrc =
        PLAYER_SILHOUETTES[theme] ?? PLAYER_SILHOUETTES.blue

    function handleOpen (event) {
        event.preventDefault()
        onOpen?.(player)
    }

    function handleKeyDown (event) {
        if (event.key === ' ') {
            event.preventDefault()
            handleOpen(event)
        }
    }

    return (
        <CardElement
            className={`player-card player-card--${theme} ${
                interactive ? '' : 'player-card--static'
            }`}
            tabIndex={interactive ? 0 : undefined}
            href={interactive ? '#' : undefined}
            onClick={interactive ? handleOpen : undefined}
            onKeyDown={interactive ? handleKeyDown : undefined}
            aria-label={
                interactive
                    ? `Ver estadísticas de ${noInfo(player.name)}`
                    : undefined
            }
        >
            <div
                className="player-card__shine"
                aria-hidden="true"
            />

            <div className="player-card__rating">
                <strong>
                    -
                </strong>

                <span>
                    {getPositionAbbreviation(player.position)}
                </span>
            </div>

            <div
                className="player-card__portrait"
                aria-hidden="true"
            >
                <img
                    src={silhouetteSrc}
                    alt=""
                    className="player-card__silhouette"
                />
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
                            {leagueLogo
                                ? (
                                    <img
                                        src={leagueLogo}
                                        alt=""
                                        className="player-card__league-logo"
                                    />
                                )
                                : (
                                    <TrophyIcon />
                                )}
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

            {interactive && (
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
            )}
        </CardElement>
    )
}