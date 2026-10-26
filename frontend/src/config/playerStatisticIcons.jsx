import {
    Activity,
    ArrowRight,
    ArrowUpRight,
    Ban,
    CircleX,
    Clock3,
    CornerUpRight,
    Crosshair,
    Flag,
    Footprints,
    GitBranch,
    Goal,
    Move,
    RectangleVertical,
    RefreshCcw,
    ScanSearch,
    Send,
    Shield,
    Star,
    Target,
    TriangleAlert
} from 'lucide-react'

const PLAYER_STATISTIC_ICONS = {
    minsPlayed: Clock3,
    goal: Goal,
    assistTotal: CornerUpRight,
    shotsPerGame: Crosshair,
    keyPassPerGame: Send,
    dribbleWonPerGame: Footprints,
    tacklePerGame: Shield,
    rating: Star,
    yellowCard: RectangleVertical,
    redCard: RectangleVertical,
    foulGivenPerGame: Activity,
    offsideGivenPerGame: Flag,
    dispossessedPerGame: CircleX,
    turnoverPerGame: RefreshCcw,
    interceptionPerGame: ScanSearch,
    foulsPerGame: TriangleAlert,
    offsideWonPerGame: Flag,
    clearancePerGame: ArrowUpRight,
    wasDribbledPerGame: Move,
    outfielderBlockPerGame: Ban,
    goalOwn: Goal,
    totalPassesPerGame: Send,
    passSuccess: Target,
    accurateCrossesPerGame: CornerUpRight,
    accurateLongPassPerGame: ArrowRight,
    accurateThroughBallPerGame: GitBranch
}

export function getPlayerStatisticIcon (key) {
    return PLAYER_STATISTIC_ICONS[key] ?? Activity
}
