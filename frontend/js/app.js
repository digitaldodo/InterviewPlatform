document.addEventListener('DOMContentLoaded', () => {
    const btnInterviewee = document.getElementById('btn-interviewee');
    const btnInterviewer = document.getElementById('btn-interviewer');

    if(btnInterviewee) {
        btnInterviewee.addEventListener('click', () => {
            alert('Redirecting to interviewee registration...');
        });
    }

    if(btnInterviewer) {
        btnInterviewer.addEventListener('click', () => {
            alert('Redirecting to interviewer registration...');
        });
    }
});
