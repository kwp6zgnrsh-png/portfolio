const postUtils = {
    toQueryParams: (search) =>{
        return {
            startDate: search.startDate,
            endDate: search.endDate,
            categoryId: search.categoryId,
            searchWord: search.searchWord,
            page: search.page,
        };
    },
    isRecentPost: (date) => {
        const createdDate = new Date(date);
        const currentDate = new Date();
        const daysToAdd = 7;
        const dateAfterAddingDays = new Date(createdDate.setDate(createdDate.getDate() + daysToAdd));

        return dateAfterAddingDays > currentDate;
    },
};

export default postUtils;
