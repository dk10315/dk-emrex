angular.module('helper', [])
        .service('helperService', function () {

            var fixReports = function (reports) {
                // Report must be an array...
                if (!angular.isArray(reports))
                    reports = [reports];

                angular.forEach(reports, function (report) {
                    var hasPart = [];

                    // learningOpportunitySpecification must be an array
                    if (!angular.isArray(report.learningOpportunitySpecification))
                        hasPart.push({learningOpportunitySpecification: report.learningOpportunitySpecification})
                    else
                        angular.forEach(report.learningOpportunitySpecification, function (specification) {
                            hasPart.push({learningOpportunitySpecification: specification});
                        });
                    report.learningOpportunitySpecification = hasPart;
                });
                return reports;
            };

            var selectedLanguage = "en";

            function getRightLanguage(titles) {
                var result = "";
                var planB = titles;
                if (angular.isArray(titles))
                    angular.forEach(titles, function (title) {

                        if (title.value)
                            planB = title.value; // anything is better than nothing

                        if (title['xml:lang'] === selectedLanguage && title.content)
                            result = title['content'];
                    });
                else
                    result = titles.content;
                if (result) {
                    return result;
                } else {
                    return planB;
                }
            }
            ;

            var calculateCourses = function (learningOpportunityArray, count) {
                var count = 0;
                angular.forEach(learningOpportunityArray, function (opportunity) {
                    if (opportunity.learningOpportunitySpecification) {
                        count++;
                        if (opportunity.learningOpportunitySpecification.hasPart)
                            count = count + calculateCourses(opportunity.learningOpportunitySpecification.hasPart)
                    }
                });
                return count;
            };

            var calculateEcts = function(learningOpportunityArray) {
                var count = 0;
                var ects; 
                angular.forEach(learningOpportunityArray, function (opportunity) {
                    if (opportunity.learningOpportunitySpecification) {
                    	ects = opportunity.learningOpportunitySpecification.specifies.learningOpportunityInstance.credit[0].value
                    	if(ects){
                    		switch(typeof(ects)){
                    			case "string":
                    				count += Number.parseFloat(ects);
                    				break;
                    			case "number":
                    				count += ects;
                    				break;
                    			default: 
                    				break;
                    		}
                    	}
                        if (opportunity.learningOpportunitySpecification.hasPart)
                            count = count + calculateCourses(opportunity.learningOpportunitySpecification.hasPart)
                    }
                });
                return count;
            };

            var filterProperReports = function (reports) {
                return reports.filter(function (report) {
                    var goodReport = true;
                    angular.forEach(report.learningOpportunitySpecification, function (object) {
                        if (!object.learningOpportunitySpecification)
                            goodReport = false;
                    });
                    return goodReport;
                });
            };

            var calculateAndFilter = function (reports) {
                angular.forEach(reports, function (report) {
                    if (report.learningOpportunitySpecification) {
                        report.numberOfCourses = calculateCourses(report.learningOpportunitySpecification);
                        report.numberOfEcts = calculateEcts(report.learningOpportunitySpecification);
                    }
                });
                return filterProperReports(reports);
            };


            return {fixReports: fixReports,
                getRightLanguage: getRightLanguage,
                calculateAndFilter: calculateAndFilter,
                filterProperReports: filterProperReports};

        });
