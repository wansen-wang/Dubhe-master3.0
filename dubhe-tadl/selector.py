from abc import ABC, abstractmethod


class Selector(ABC):
    """
    choose the best model from a group of candidates.
    To implement a new selector, users need to implement:
		method: "fit"
		method: "__init__"
		    super().__init__() must be called in __init__ method

    parameters:
    -----------
    candidates: candidates to be evaluated

    ##### Examples #####

    # class HPOSelector(Selector):
    #     def __init__(self, *args, single_candidate=True):
    #         super().__init__(single_candidate)
    #         self.args = args

    #     def fit(self):
    #    
    #         # only one candatite, function passed
    #         
    #         pass

    ###########

    """

    @abstractmethod
    def __init__(self, single_candidate=True):
        self.single_candidate = single_candidate
        self._valid()

    @abstractmethod
    def fit(self, candidates=None):
        """
        evaluate the candidates to select the best one.
        any optimization algos could be implement here.
        if the inputs has only one candidates, just return the candidate directly
        """
        raise NotImplementedError

    def _valid(self, ):
        if self.single_candidate:
            print("### single model, selecting finished ###")
            exit(0)






