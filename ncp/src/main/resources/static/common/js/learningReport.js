angular.module('learningReport', [])
    .directive('learningReportDirective', function (selectedCoursesService, helperService) {
        return {
            restrict: "E",
            replace: true,
            scope: {
                report: '=',
                typeFilter: '=',
                levelFilter: '=',
                onlyViewing: '=',
                onlyViewing: '=',
                startDate: '=',
                endDate: '='
            },
            templateUrl: 'common/partials/learningReport.html',
            controller: function ($scope) {

                $scope.selectAll = true;

                $scope.selectAllClicked = function () {
                    angular.forEach($scope.flattenedLearningOpportunities, function (opportunity) {
                        opportunity.selected = $scope.selectAll;
                        $scope.checkBoxChanged(opportunity);
                    });
                };

                if (!angular.isArray($scope.report.learningOpportunitySpecification))
                    $scope.report.learningOpportunitySpecification = [{learningOpportunitySpecification: $scope.report.learningOpportunitySpecification}];

                $scope.getRightLanguage = helperService.getRightLanguage;

                function unselectInvisibleOpportunity(visible, opportunity) {
                    if (!visible) {
                        opportunity.selected = false;
                        $scope.checkBoxChanged(opportunity);
                    }
                }

                $scope.selectedTypes = function (opportunity) {
                    var visible = ($scope.onlyViewing) || $scope.typeFilter[opportunity.type];
                    unselectInvisibleOpportunity(visible, opportunity);
                    return visible;
                };

                $scope.selectedLevel = function (opportunity) {
                    var creditLevel = opportunity.specifies.learningOpportunityInstance.credit && opportunity.specifies.learningOpportunityInstance.credit.level;
                    var visible = ($scope.onlyViewing || $scope.levelFilter == "Any") || ($scope.levelFilter == creditLevel);
                    unselectInvisibleOpportunity(visible, opportunity);
                    return visible;
                };

                $scope.selectedTimeInterval = function (opportunity) {
                    if (!opportunity.specifies.learningOpportunityInstance.date)
                        return true;

                    var date = Date.parse(opportunity.specifies.learningOpportunityInstance.date);
                    var startDate;
                    var endDate;
                    if ($scope.startDate)
                        startDate = Date.parse($scope.startDate);

                    if ($scope.endDate)
                        endDate = Date.parse($scope.endDate);


                    var visible = ($scope.onlyViewing ||
                    (!endDate || endDate >= date)
                    && (!startDate || startDate <= date));
                    unselectInvisibleOpportunity(visible, opportunity);
                    return visible;
                };

                function recursiveOpportunityFlattening(learningOpportunityArray, partOf) {
                    angular.forEach(learningOpportunityArray, function (opportunityWrapper) {

                        if (opportunityWrapper) {

                            // in some cases learningopportunityspecification is an array. some cases not..
                            if (opportunityWrapper.learningOpportunitySpecification)
                                var opportunity = opportunityWrapper.learningOpportunitySpecification;
                            else
                                var opportunity = opportunityWrapper;


                            // Add Elmo identifier
                            if (angular.isArray(opportunity.identifier))
                                angular.forEach(opportunity.identifier, function (identifier) {
                                    if (identifier.type == "elmo")
                                        opportunity.elmoIdentifier = identifier.value;
                                    if (identifier.type == "local" || identifier.type == "oodi" ) {//TODO remove oodi
                                        opportunity.localIdentifier = identifier.value;
                                    	opportunity.elmoIdentifier = identifier.value;
                                    }
                                })
                            else if (opportunity.identifier) {
                            	if (typeof opportunity.identifier.value !== "undefined") {
                                    opportunity.elmoIdentifier = opportunity.identifier.value;
                                    opportunity.localIdentifier = opportunity.identifier.value;
                            	} else {
                                    opportunity.elmoIdentifier = opportunity.identifier.value;
                                    opportunity.localIdentifier = opportunity.identifier.value;
                            	}
                            }
                            // Find parents Elmo identifier
                            if (partOf && partOf.elmoIdentifier)
                                opportunity.partOf = partOf.elmoIdentifier
                            else
                                opportunity.partOf = '-';

                            flatArray.push(opportunity);

                            // Add properties for table
                            if (opportunity.selected === undefined) {
                                opportunity.selected = true;
                                selectedCoursesService.addId(opportunity.elmoIdentifier); // all are selected at the beginning
                            }
                            // Recursion
                            if (opportunity.hasPart)
                                recursiveOpportunityFlattening(opportunity.hasPart, opportunity)
                        }
                    });
                    return flatArray;
                }
                ;

                var flatArray = [];
                flatArray = recursiveOpportunityFlattening($scope.report.learningOpportunitySpecification);

                // Sort by date
                flatArray.sort(function(a, b) {
                    var d1 = a.specifies.learningOpportunityInstance.date || 0; //1970-01-01
                    var d2 = b.specifies.learningOpportunityInstance.date || 0; //1970-01-01
                    return d1 == d2 ? 0 : d1 > d2 ? -1 : 1;
                });

                $scope.flattenedLearningOpportunities = flatArray;

                if (typeof $scope.report.issuer !== "undefined") {
                    $scope.issuerName = helperService.getRightLanguage($scope.report.issuer.title);
                } else {
                    $scope.issuerName = "TODO : unknown issuer";
                }

                var selectParent = function (child) {
                    if (child.partOf != '-')
                        angular.forEach($scope.flattenedLearningOpportunities, function (possibleParent) {
                            if (possibleParent.elmoIdentifier == child.partOf) {
                                possibleParent.selected = true;
                                $scope.checkBoxChanged(possibleParent);
                            }
                        });
                };

                var deselectChilds = function (parent) {
                    angular.forEach($scope.flattenedLearningOpportunities, function (possibleChild) {
                        if (parent.elmoIdentifier == possibleChild.partOf) {
                            possibleChild.selected = false;
                            $scope.checkBoxChanged(possibleChild);
                        }
                    })
                }


                $scope.checkBoxChanged = function (opportunity) {
                    if (opportunity.selected) {
                        selectedCoursesService.addId(opportunity.elmoIdentifier)
                        selectParent(opportunity);

                    }
                    else {
                        selectedCoursesService.removeId(opportunity.elmoIdentifier);
                        deselectChilds(opportunity);
                    }
                }
            }
        }
    });
