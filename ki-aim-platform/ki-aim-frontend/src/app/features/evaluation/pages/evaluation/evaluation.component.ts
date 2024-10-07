import {Component} from '@angular/core';
import {EvaluationService} from "../../services/evaluation.service";
import {TitleService} from "../../../../core/services/title-service.service";
import {ProcessStatus} from "../../../../core/enums/process-status";
import {environments} from "../../../../../environments/environment";
import {HttpClient} from "@angular/common/http";
import {map, Observable, of, tap} from "rxjs";

@Component({
    selector: 'app-evaluation',
    templateUrl: './evaluation.component.html',
    styleUrls: ['./evaluation.component.less'],
})
export class EvaluationComponent {
    protected readonly ProcessStatus = ProcessStatus;

    private _result: any | null = null;

    constructor(
        protected readonly evaluationService: EvaluationService,
        private readonly http: HttpClient,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Evaluation");
    }

    ngOnInit() {
        this.evaluationService.fetchStatus();
        // this.fetchResult();
    }


    protected getResult(): Observable<any> {
        if (this._result !== null) {
            return of(this._result);
        } else {
            return this.fetchResult().pipe(
                map(value => {
                    return JSON.parse(value);
                }),
                tap(value => {
                    this._result = value;
                }),
            );
        }
    }

    protected result(): any {
        return {
            "resemblance": {
                "mean": {
                    "real": {
                        "id": 496785.71023965144,
                        "birthdate": -1168390497.7099237,
                        "death_date": 528257882.35294116,
                        "Age": 53.510893246187365,
                        "RestingBP": 132.39651416122004,
                        "Cholesterol": 198.7995642701525,
                        "MaxHR": 136.80936819172115,
                        "Oldpeak": 0.8873638344226579
                    },
                    "synthetic": {
                        "id": -124762.17379115,
                        "birthdate": -0.0057,
                        "death_date": -0.2013,
                        "Age": 77.9279715618,
                        "RestingBP": 184.15605723000002,
                        "Cholesterol": 135.75490968965,
                        "MaxHR": 190.05292995899998,
                        "Oldpeak": 2.71255499090377
                    }
                },
                "standard_deviation": {
                    "real": {
                        "id": 286789.2752585028,
                        "birthdate": 530939767.0388105,
                        "death_date": 614797560.213518,
                        "Age": 9.427477516153495,
                        "RestingBP": 18.504067410295093,
                        "Cholesterol": 109.32455089779256,
                        "MaxHR": 25.446463075309385,
                        "Oldpeak": 1.065989072198759
                    },
                    "synthetic": {
                        "id": 79927.50046428297,
                        "birthdate": 0.07528663100232849,
                        "death_date": 0.5473648425201263,
                        "Age": 2.8363812474017203,
                        "RestingBP": 11.998748040942731,
                        "Cholesterol": 81.40650413447196,
                        "MaxHR": 8.76807961439011,
                        "Oldpeak": 0.6773825755099439
                    }
                },
                "skewness": {
                    "real": {
                        "id": 0.013943506440715037,
                        "birthdate": -0.15432745649603186,
                        "death_date": -0.17715188842768795,
                        "Age": -0.19603936751803766,
                        "RestingBP": 0.1799369143410541,
                        "Cholesterol": -0.6104175432769176,
                        "MaxHR": -0.1444377667298132,
                        "Oldpeak": 1.0234271657157934
                    },
                    "synthetic": {
                        "id": 5.89130103199034,
                        "birthdate": -13.133776161684374,
                        "death_date": 0.031549185056952145,
                        "Age": 2.319204176887207,
                        "RestingBP": -2.6527087936453126,
                        "Cholesterol": 1.1940417499341658,
                        "MaxHR": 2.2208325011223824,
                        "Oldpeak": -0.8593888107560232
                    }
                },
                "mode": {
                    "name": {"real": "Abbie Casper", "synthetic": "Ronnie Kautzer"},
                    "Sex": {"real": "M", "synthetic": "M"},
                    "ChestPainType": {"real": "ASY", "synthetic": "ASY"},
                    "FastingBS": {"real": false, "synthetic": false},
                    "RestingECG": {"real": "Normal", "synthetic": "Normal"},
                    "ExerciseAngina": {"real": "N", "synthetic": "N"},
                    "ST_Slope": {"real": "Flat", "synthetic": "Up"},
                    "HeartDisease": {"real": true, "synthetic": true}
                },
                "quantiles": {
                    "real": {
                        "id": [211282.2, 391894.8000000001, 598632.3999999999, 796683.8],
                        "birthdate": [-1762974720.0, -1249292159.9999998, -971377920.0000001, -697766399.9999998],
                        "death_date": [-64108799.99999994, 402278400.0000003, 738599040.0, 1082108160.0000002],
                        "Age": [45.0, 52.0, 56.799999999999955, 62.0],
                        "RestingBP": [120.0, 128.0, 135.0, 145.0],
                        "Cholesterol": [136.20000000000016, 209.0, 238.0, 276.0],
                        "MaxHR": [115.0, 130.0, 144.0, 160.0],
                        "Oldpeak": [0.0, 0.0, 1.0, 1.8]
                    },
                    "synthetic": {
                        "id": [-140466.48, -139601.17200000002, -137538.69, -131778.048],
                        "birthdate": [0.0, 0.0, 0.0, 0.0],
                        "death_date": [-1.0, 0.0, 0.0, 0.0],
                        "Age": [77.4222232, 77.558786, 77.6055964, 77.624116],
                        "RestingBP": [187.075768, 187.423242, 187.53177399999998, 187.57940399999998],
                        "Cholesterol": [67.95071, 94.38376600000001, 132.450248, 197.63257200000007],
                        "MaxHR": [186.517436, 186.787044, 186.869104, 186.89226],
                        "Oldpeak": [2.21274, 2.70627052, 3.0327992200000002, 3.26288368]
                    }
                },
                "kurtosis": {
                    "real": {
                        "id": -1.1735468371741575,
                        "birthdate": -0.8882807561252353,
                        "death_date": -0.4572392147336024,
                        "Age": -0.3832864096294757,
                        "RestingBP": 3.27806645322641,
                        "Cholesterol": 0.12160807050415912,
                        "MaxHR": -0.4454619038133698,
                        "Oldpeak": 1.2076385934425478
                    },
                    "synthetic": {
                        "id": 35.89899869374085,
                        "birthdate": 170.53018226235955,
                        "death_date": 0.39462322985707354,
                        "Age": 11.892311813452457,
                        "RestingBP": 7.966221199088549,
                        "Cholesterol": 1.1091884369589287,
                        "MaxHR": 3.4787262283428206,
                        "Oldpeak": 2.4085058368028878
                    }
                },
                "ranges": {
                    "real": {
                        "id": {"min": 1306.0, "max": 999730.0},
                        "birthdate": {"min": -2175292800.0, "max": 64800000.0},
                        "death_date": {"min": -1113523200.0, "max": 2498256000.0},
                        "Age": {"min": 28.0, "max": 77.0},
                        "RestingBP": {"min": 0.0, "max": 200.0},
                        "Cholesterol": {"min": 0.0, "max": 603.0},
                        "MaxHR": {"min": 60.0, "max": 202.0},
                        "Oldpeak": {"min": -2.6, "max": 6.2}
                    },
                    "synthetic": {
                        "id": {"min": -307888.16, "max": 476993.06},
                        "birthdate": {"min": -1.0, "max": 0.0},
                        "death_date": {"min": -1.0, "max": 2.0},
                        "Age": {"min": 64.85876, "max": 89.49691},
                        "RestingBP": {"min": 133.62871, "max": 233.45634},
                        "Cholesterol": {"min": -74.006905, "max": 460.14532},
                        "MaxHR": {"min": 168.96913, "max": 222.85674},
                        "Oldpeak": {"min": -0.3400646, "max": 7.5165668}
                    }
                },
                "kolmogorov_smirnov": {
                    "id": {"KS_statistic": 0.9773, "p_value": 1.5e-323},
                    "birthdate": {"KS_statistic": 0.9978237214363439, "p_value": 0.0},
                    "death_date": {"KS_statistic": 0.7758433079434167, "p_value": 2.02e-321},
                    "Age": {"KS_statistic": 0.9807474428726877, "p_value": 0.0},
                    "RestingBP": {"KS_statistic": 0.9125948857453754, "p_value": 3.26e-322},
                    "Cholesterol": {"KS_statistic": 0.4969398258977149, "p_value": 1.534820204096293e-191},
                    "MaxHR": {"KS_statistic": 0.9764253536452666, "p_value": 2e-323},
                    "Oldpeak": {"KS_statistic": 0.7456860718171926, "p_value": 2.574e-321}
                },
                "hellinger_distance": {
                    "name": 0.5060677973757779,
                    "Sex": 0.04944555737506244,
                    "ChestPainType": 0.13185988074916433,
                    "FastingBS": 0.07582565243044732,
                    "RestingECG": 0.11562840569902169,
                    "ExerciseAngina": 0.12149810840847466,
                    "ST_Slope": 0.17726756878977648,
                    "HeartDisease": 0.1683752960264406
                }
            }, "utility": {}
        }
    }

    protected getReal(obj: any) {
        return obj['real'];
    }

    protected getSyntheticAttribute(obj: any, attribute: any) {
        return obj['synthetic'][attribute];
    }

    protected getValues(obj: any): any {
        return obj.value;
    }

    protected isObject(abc: any): boolean {
        return typeof abc === "object";
    }

    protected isNormalTable(name: any): boolean {
        return ['mean', 'standard_deviation', 'skewness', 'quantiles', 'kurtosis'].includes(name);
    }


    /**
     * Downloads all files related to the project in a ZIP file.
     * @protected
     */
    protected downloadResult() {
        this.http.get(environments.apiUrl + "/api/project/zip", {responseType: 'arraybuffer'}).subscribe({
            next: data => {
                const blob = new Blob([data], {
                    type: 'application/zip'
                });
                const url = window.URL.createObjectURL(blob);
                window.open(url);
            }
        });
    }

    protected readonly JSON = JSON;
    protected readonly Object = Object;


    private fetchResult(): Observable<string> {
        return this.http.get<string>(environments.apiUrl + `/api/project/resultFile`,
            {
                params: {
                    executionStepName: 'EVALUATION',
                    processStepName: 'TECHNICAL_EVALUATION',
                    name: 'metrics.json',
                }
            });
    }
}
