import type { InterviewQuestion } from '@/api/interviewSeed'
/** Only questions returned by this chapter may enable authored references. */
export function bindChapterQuizReferences(root:HTMLElement,questions:InterviewQuestion[]) {
 for(const button of root.querySelectorAll<HTMLButtonElement>('[data-quiz-ref-key]')) {
  const key=button.dataset.quizRefKey
  const question=questions.find(q=>Number.isSafeInteger(q.id)&&q.id>0&&(q.canonicalKey===key||String(q.id)===key))
  button.disabled=!question
  button.textContent=question ? `练习：${question.title}` : '此题未关联本章，暂不可练习'
  if(question)button.dataset.questionTarget=String(question.id)
  else delete button.dataset.questionTarget
 }
}
