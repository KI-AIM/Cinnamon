import { Component, Input } from "@angular/core";

interface ProcessProgressValue {
    progress: number;
    processed: number;
    total: number;
    phase?: string;
    modelProgress?: ModelProgressValue[];
}

interface ModelProgressValue {
    model: string;
    progress: number;
    processed: number;
    total: number;
    status: string;
}

@Component({
    selector: "app-process-progress",
    templateUrl: "./process-progress.component.html",
    styleUrls: ["./process-progress.component.less"],
    standalone: false,
})
export class ProcessProgressComponent {
    @Input() public status: string | null | undefined = null;
    @Input() public running = false;
    @Input() public unitLabel = "items";

    public get progress(): ProcessProgressValue | null {
        if (this.status == null) {
            return null;
        }

        try {
            const parsed: unknown = JSON.parse(this.status);
            if (typeof parsed !== "object" || parsed === null) {
                return null;
            }

            const value = parsed as Record<string, unknown>;
            const progress = Number(value["progress"]);
            const processed = Number(value["processed"]);
            const total = Number(value["total"]);

            if (!Number.isFinite(progress) || !Number.isFinite(processed) || !Number.isFinite(total)) {
                return null;
            }

            return {
                progress: Math.min(100, Math.max(0, progress)),
                processed: Math.max(0, processed),
                total: Math.max(0, total),
                phase: typeof value["phase"] === "string" ? value["phase"] : undefined,
                modelProgress: this.parseModelProgress(value["model_progress"]),
            };
        } catch {
            return null;
        }
    }

    private parseModelProgress(rawValue: unknown): ModelProgressValue[] | undefined {
        if (typeof rawValue !== "string") {
            return undefined;
        }

        try {
            const parsed: unknown = JSON.parse(rawValue);
            if (!Array.isArray(parsed)) {
                return undefined;
            }

            const models = parsed.filter((entry): entry is Record<string, unknown> => (
                typeof entry === "object" && entry !== null
            ));
            const progress = models.map((model): ModelProgressValue | null => {
                const name = model["model"];
                const status = model["status"];
                const processed = Number(model["processed"]);
                const total = Number(model["total"]);

                if (
                    typeof name !== "string"
                    || typeof status !== "string"
                    || !Number.isFinite(processed)
                    || !Number.isFinite(total)
                ) {
                    return null;
                }

                return {
                    model: name,
                    status,
                    processed: Math.max(0, processed),
                    total: Math.max(0, total),
                    progress: total === 0
                        ? 100
                        : Math.min(100, Math.max(0, processed / total * 100)),
                };
            }).filter((model): model is ModelProgressValue => model !== null);

            return progress.length > 0 ? progress : undefined;
        } catch {
            return undefined;
        }
    }
}
