import angular from 'angular';

var presetLabelService = angular.module('kahuna.services.presetLabel', []);

presetLabelService.factory('presetLabelService',
                            ['$window', function ($window) {

    const presetLabelsKey = 'preset labels';

    function getLabels() {
        return JSON.parse($window.localStorage.getItem(presetLabelsKey));
    }

    function addLabels(newLabels) {
        let labels = new Set(getLabels());
        newLabels.map( newLabel => labels.add(newLabel) );
        setLabels(labels);
    }

    function removeLabel(label) {
        let labels = new Set(getLabels());
        labels.delete(label);
        setLabels(labels);
    }

    function setLabels(updatedLabels) {
        const labels = Array.from(updatedLabels);
        $window.localStorage.setItem(presetLabelsKey, JSON.stringify(labels) );
    }

    return {
        getLabels,
        addLabels,
        removeLabel
    };

}]);

export default presetLabelService;
