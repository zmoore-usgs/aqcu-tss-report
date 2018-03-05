package gov.usgs.aqcu;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aquaticinformatics.aquarius.sdk.timeseries.servicemodels.Publish.ProcessorListServiceResponse;
import com.aquaticinformatics.aquarius.sdk.timeseries.servicemodels.Publish.RatingCurveListServiceResponse;
import com.aquaticinformatics.aquarius.sdk.timeseries.servicemodels.Publish.TimeSeriesDescriptionListByUniqueIdServiceResponse;
import com.google.gson.Gson;

import gov.usgs.aqcu.builder.TimeSeriesSummaryReportBuilderService;
import gov.usgs.aqcu.client.JavaToRClient;
import gov.usgs.aqcu.model.TimeSeriesSummaryReport;
import gov.usgs.aqcu.retrieval.RatingCurveListService;
import gov.usgs.aqcu.retrieval.TimeSeriesMetadataService;
import gov.usgs.aqcu.retrieval.UpchainProcessorListService;

@RestController
@RequestMapping("/timeseriessummary")
public class Controller {
	private static final Logger LOG = LoggerFactory.getLogger(Controller.class);
	private Gson gson;
	private TimeSeriesMetadataService timeSeriesMetadataService;
	private RatingCurveListService ratingCurveListService;
	private UpchainProcessorListService upchainProcessorListService;
	private TimeSeriesSummaryReportBuilderService reportBuilderService;
	private JavaToRClient javaToRClient;

	@Autowired
	public Controller(
		TimeSeriesMetadataService timeSeriesMetadataService, 
		UpchainProcessorListService upchainProcessorListService, 
		RatingCurveListService ratingCurveListService,
		TimeSeriesSummaryReportBuilderService reportBuilderService,
		JavaToRClient javaToRClient,
		Gson gson) {
		this.timeSeriesMetadataService = timeSeriesMetadataService;
		this.upchainProcessorListService = upchainProcessorListService;
		this.ratingCurveListService = ratingCurveListService;
		this.reportBuilderService = reportBuilderService;
		this.javaToRClient = javaToRClient;
		this.gson = gson;
	}

	@GetMapping(produces="text/html")
	public byte[] getReport(
			@RequestParam String primaryTimeseriesIdentifier,
			@RequestParam String station,
			@RequestParam(required=false) String lastMonths,
			@RequestParam(required=false) String waterYear,
			@RequestParam(required=false) String startDateString,
			@RequestParam(required=false) String endDateString,
			@RequestParam(required=false) String excludedCorrections) {	

		Instant startDate = null;
		Instant endDate = null;

		if (StringUtils.isNumeric(startDateString)) {
			startDate = Instant.parse(startDateString);
		}
		if (StringUtils.isNumeric(endDateString)) {
			endDate = Instant.parse(endDateString);
		}
		String requestingUser = "tesUser";

		//Fetch Time Series Descriptions
		TimeSeriesDescriptionListByUniqueIdServiceResponse metadataResponse = timeSeriesMetadataService.get(primaryTimeseriesIdentifier);
		
		//Fetch Location Descriptions


		//Fetch Upchain Processors
		ProcessorListServiceResponse processorsResponse = upchainProcessorListService.get(primaryTimeseriesIdentifier, startDate, endDate);

		//Fetch Rating Curves IFF we got at least one upchain processor to pull the rating model identifier from
		RatingCurveListServiceResponse ratingCurvesResponse = null;
		if(processorsResponse != null && processorsResponse.getProcessors() != null && processorsResponse.getProcessors().size() > 0) {
			ratingCurvesResponse = ratingCurveListService.get(processorsResponse.getProcessors().get(0).getInputRatingModelIdentifier(), null, startDate, endDate);
		}

		//Build the TSS Report JSON
		TimeSeriesSummaryReport report = reportBuilderService.buildTimeSeriesSummaryReport(metadataResponse, ratingCurvesResponse, startDate, endDate, requestingUser);

		return javaToRClient.render(requestingUser, "timeseriessummary", gson.toJson(report, TimeSeriesSummaryReport.class));
	}

}