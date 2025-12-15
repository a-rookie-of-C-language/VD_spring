package site.arookieofc.service.BO;

public enum ActivityStatus {
    EnrollmentNotStart,
    EnrollmentStarted,
    EnrollmentEnded,
    ActivityStarted,
    ActivityEnded,
    UnderReview,
    FailReview;

    public boolean canTransitionTo(ActivityStatus target) {
        if (this == target) {
            return true; // Same state is always a valid "transition" (idempotent)
        }

        return switch (this) {
            case UnderReview -> target == EnrollmentNotStart || target == FailReview;
            case EnrollmentNotStart -> target == EnrollmentStarted;
            case EnrollmentStarted -> target == EnrollmentEnded;
            case EnrollmentEnded -> target == ActivityStarted;
            case ActivityStarted -> target == ActivityEnded;
            case ActivityEnded, FailReview -> false; // Terminal states cannot transition
            default -> false;
        };
    }


    public boolean isTerminalState() {
        return this == ActivityEnded || this == FailReview;
    }

    public boolean isProtectedState() {
        return this == UnderReview || this == FailReview;
    }
}

