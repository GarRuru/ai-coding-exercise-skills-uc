package tw.teddysoft.example.plan.entity;

import tw.teddysoft.example.common.DateProvider;
import tw.teddysoft.example.tag.entity.TagId;
import tw.teddysoft.ezddd.entity.EsAggregateRoot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static java.lang.String.format;
import static tw.teddysoft.ucontract.Contract.*;

public class Plan extends EsAggregateRoot<PlanId, PlanEvents> {
    public final static String CATEGORY = "Plan";
    private PlanId id;   // EsAggregateRoot does NOT provide an id field — declare it here (see aggregate.md Step 9.5)
    private String name;
    private String userId;
    private Map<ProjectId, Project> projects;
    private int nextTaskId;

    // Constructor for event sourcing framework to rebuild aggregate from events
    public Plan(List<PlanEvents> domainEvents) {
        super(domainEvents);
    }

    // Public constructor for creating new instances
    public Plan(PlanId planId, String name, String userId) {
        super();

        requireNotNull("Plan id", planId);
        requireNotNull("Plan name", name);
        requireNotNull("User id", userId);

        apply(new PlanEvents.PlanCreated(
                planId,
                name,
                userId,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure(format("Plan id is '%s'", planId), () -> _planIdMatches(planId));
        ensure(format("Plan name is '%s'", name), () -> _planNameMatches(name));
        ensure(format("User id is '%s'", userId), () -> _planUserMatches(userId));
        ensure("A PlanCreated event is generated correctly", () ->
            _planCreatedEventGenerated(planId, name, userId));
    }

    public String getName() {
        return name;
    }

    public String getUserId() {
        return userId;
    }

    public void rename(String newName) {
        requireNotNull("New name", newName);
        require("New name not empty", () -> _notBlank(newName));
        require("New name is different", () -> _nameDifferent(newName));

        String oldName = this.name;
        
        apply(new PlanEvents.PlanRenamed(
                getId(),
                newName,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure(format("Plan name is changed to '%s'", newName), () -> _planNameMatches(newName));
        ensure("A PlanRenamed event is generated correctly", () ->
            _planRenamedEventGenerated(newName));
    }

    public void createProject(ProjectId projectId, ProjectName projectName) {
        requireNotNull("Project id", projectId);
        requireNotNull("Project name", projectName);
        require("Project id must be unique", () -> _projectAbsent(projectId));

        apply(new PlanEvents.ProjectCreated(
                getId(),
                projectId,
                projectName,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure(format("Project with id '%s' exists", projectId), () -> _projectExists(projectId));
        ensure(format("Project name is '%s'", projectName), () -> _projectNameMatches(projectId, projectName));
        ensure("A ProjectCreated event is generated correctly", () ->
            _projectCreatedEventGenerated(projectId, projectName));
    }

    public boolean hasProject(ProjectId projectId) {
        return projects.containsKey(projectId);
    }

    public Project getProject(ProjectId projectId) {
        return projects.get(projectId);
    }
    
    public boolean hasProject(ProjectName projectName) {
        return projects.values().stream()
                .anyMatch(project -> project.getName().equals(projectName));
    }
    
    public Project getProject(ProjectName projectName) {
        return projects.values().stream()
                .filter(project -> project.getName().equals(projectName))
                .findFirst()
                .orElse(null);
    }
    
    public Map<ProjectId, Project> getProjects() {
        return new HashMap<>(projects);
    }

    public TaskId createTask(ProjectName projectName, TaskId taskId, String taskName) {
        requireNotNull("Project name", projectName);
        requireNotNull("Task name", taskName);
        require("Task name not empty", () -> _notBlank(taskName));
        
        // Find project by name
        Project project = projects.values().stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst()
                .orElse(null);
        require("Project must exist", () -> _projectReferenceExists(project));
        
        // Create task in project
        project.createTask(taskId, taskName);
        
        apply(new PlanEvents.TaskCreated(
                getId(),
                project.getId(),
                taskId,
                taskName,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure(format("Task with id '%s' exists in project '%s'", taskId, projectName),
                () -> _projectHasTask(project, taskId));
        ensure(format("Task name is '%s'", taskName), () -> _taskNameMatches(project, taskId, taskName));
        ensure("A TaskCreated event is generated correctly", () ->
            _taskCreatedEventGenerated(project.getId(), taskId, taskName));

        return taskId;
    }

    public boolean hasTask(TaskId taskId) {
        return projects.values().stream()
                .anyMatch(project -> project.hasTask(taskId));
    }

    public Task getTask(TaskId taskId) {
        return projects.values().stream()
                .filter(project -> project.hasTask(taskId))
                .map(project -> project.getTask(taskId))
                .findFirst()
                .orElse(null);
    }
    
    public void checkTask(ProjectName projectName, TaskId taskId) {
        // Find project by name
        Project project = projects.values().stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst()
                .orElse(null);
        require("Project must exist", () -> _projectReferenceExists(project));
        require("Task must exist in project", () -> _projectHasTask(project, taskId));
        
        Task task = project.getTask(taskId);
        require("Task must not already be done", () -> _taskNotDone(task));
        
        // Delegate task checking to project
        project.checkTask(taskId);
        
        apply(new PlanEvents.TaskChecked(
                getId(),
                project.getId(),
                taskId,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure(format("Task with id '%s' is done", taskId), () -> _taskDone(project, taskId));
        ensure("A TaskChecked event is generated correctly", () ->
            _taskCheckedEventGenerated(project.getId(), taskId));
    }
    
    public void uncheckTask(ProjectName projectName, TaskId taskId) {
        requireNotNull("Project name", projectName);
        requireNotNull("Task id", taskId);
        
        // Find project by name
        Project project = projects.values().stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst()
                .orElse(null);
        require("Project must exist", () -> _projectReferenceExists(project));
        require("Task must exist in project", () -> _projectHasTask(project, taskId));
        
        Task task = project.getTask(taskId);
        require("Task must be done", () -> _taskDone(task));
        
        // Delegate task unchecking to project
        project.uncheckTask(taskId);
        
        apply(new PlanEvents.TaskUnchecked(
                getId(),
                project.getId(),
                taskId,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure(format("Task with id '%s' is not done", taskId), () -> _taskNotDone(project, taskId));
        ensure("A TaskUnchecked event is generated correctly", () ->
            _taskUncheckedEventGenerated(project.getId(), taskId));
    }

    public void deleteProject(ProjectId projectId) {
        requireNotNull("Project id", projectId);
        require("Plan must not be deleted", this::_planNotDeleted);
        require("Project must exist", () -> _projectExists(projectId));
        
        // Check if project has any tasks
        Project project = getProject(projectId);
        require("Project must not have any tasks", () -> _projectHasNoTasks(project));
        
        apply(new PlanEvents.ProjectDeleted(
                getId(),
                projectId,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));
        
        ensure(format("Project with id '%s' is deleted", projectId), () -> _projectAbsent(projectId));
        ensure("A ProjectDeleted event is generated correctly", () ->
            _projectDeletedEventGenerated(projectId));
    }

    public void deleteTask(ProjectName projectName, TaskId taskId) {
        requireNotNull("Project name", projectName);
        requireNotNull("Task id", taskId);
        require("Plan must not be deleted", this::_planNotDeleted);
        require("Project must exist", () -> _projectExists(projectName));

        Project project = getProject(projectName);
        require("Task must exist in project", () -> _projectHasTask(project, taskId));
        
        // Delete the task
        project.deleteTask(taskId);
        
        apply(new PlanEvents.TaskDeleted(
                getId(),
                project.getId(),
                taskId,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));
        
        ensure(format("Task with id '%s' is deleted from project '%s'", taskId, projectName),
                () -> _taskDeletedFromProject(project, taskId));
        ensure("A TaskDeleted event is generated correctly", () ->
            _taskDeletedEventGenerated(project.getId(), taskId));
    }

    public void setTaskDeadline(ProjectName projectName, TaskId taskId, LocalDate deadline) {
        requireNotNull("Project name", projectName);
        requireNotNull("Task id", taskId);
        // deadline can be null (to remove deadline)
        require("Plan must not be deleted", this::_planNotDeleted);
        require("Project must exist", () -> _projectExists(projectName));
        
        Project project = getProject(projectName);
        require("Task must exist in project", () -> _projectHasTask(project, taskId));
        
        // Set the task deadline
        project.setTaskDeadline(taskId, deadline);
        
        apply(new PlanEvents.TaskDeadlineSet(
                getId(),
                project.getId(),
                taskId,
                deadline != null ? deadline.toString() : null,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));
        
        ensure(format("Task deadline is set for task '%s' in project '%s'", taskId, projectName),
                () -> _taskDeadlineMatches(project, taskId, deadline));
        ensure("A TaskDeadlineSet event is generated correctly", () ->
            _taskDeadlineSetEventGenerated(project.getId(), taskId, deadline));
    }

    public void renameTask(ProjectName projectName, TaskId taskId, String newName) {
        requireNotNull("Project name", projectName);
        requireNotNull("Task id", taskId);
        requireNotNull("New task name", newName);
        require("Plan must not be deleted", this::_planNotDeleted);
        require("Project must exist", () -> _projectExists(projectName));
        require("New task name must not be empty", () -> _notBlank(newName));
        
        Project project = getProject(projectName);
        require("Task must exist in project", () -> _projectHasTask(project, taskId));
        
        // Rename the task
        project.renameTask(taskId, newName);
        
        apply(new PlanEvents.TaskRenamed(
                getId(),
                project.getId(),
                taskId,
                newName,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));
        
        ensure(format("Task '%s' is renamed to '%s' in project '%s'", taskId, newName, projectName),
                () -> _taskNameMatches(project, taskId, newName));
        ensure("A TaskRenamed event is generated correctly", () ->
            _taskRenamedEventGenerated(project.getId(), taskId, newName));
    }

    public void delete() {
        require("Plan must not already be deleted", this::_planNotDeleted);
        
        apply(new PlanEvents.PlanDeleted(
                getId(),
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));
        
        ensure("Plan is marked as deleted", this::_planDeletedFlagSet);
        ensure("A PlanDeleted event is generated correctly", () ->
            _planDeletedEventGenerated());
    }

    // getId() — Entity interface requires this; AggregateRoot/EsAggregateRoot do NOT implement it (aggregate.md Step 9.5)
    @Override
    public PlanId getId() {
        return id;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    public void assignTag(ProjectId projectId, TaskId taskId, TagId tagId) {
        requireNotNull("Project id", projectId);
        requireNotNull("Task id", taskId);
        requireNotNull("Tag id", tagId);
        require("Plan is not deleted", this::_planNotDeleted);
        require("Project exists", () -> _projectExists(projectId));

        Project project = getProject(projectId);
        require("Task exists in project", () -> _projectHasTask(project, taskId));

        Task task = project.getTask(taskId);
        require("Tag not already assigned", () -> _tagNotAssigned(task, tagId));
        
        apply(new PlanEvents.TagAssigned(
                getId(),
                project.getId(),
                taskId,
                tagId,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure("Tag is assigned to task", () -> _tagAssigned(task, tagId));
        ensure("A TagAssigned event is generated correctly", () ->
            _tagAssignedEventGenerated(project.getId(), taskId, tagId));
    }
    
    public void unassignTag(ProjectId projectId, TaskId taskId, TagId tagId) {
        requireNotNull("Project id", projectId);
        requireNotNull("Task id", taskId);
        requireNotNull("Tag id", tagId);
        require("Plan is not deleted", this::_planNotDeleted);
        require("Project exists", () -> _projectExists(projectId));

        Project project = getProject(projectId);
        require("Task exists in project", () -> _projectHasTask(project, taskId));

        Task task = project.getTask(taskId);
        require("Tag is assigned", () -> _tagAssigned(task, tagId));
        
        apply(new PlanEvents.TagUnassigned(
                getId(),
                project.getId(),
                taskId,
                tagId,
                new HashMap<>(),  // metadata
                UUID.randomUUID(),
                DateProvider.now()
        ));
        
        ensure("Tag is unassigned from task", () -> _tagUnassigned(task, tagId));
        ensure("A TagUnassigned event is generated correctly", () ->
            _tagUnassignedEventGenerated(project.getId(), taskId, tagId));
    }

    @Override
    public void ensureInvariant() {
        invariant(format("Category is '%s'.", getCategory()), this::_categoryMatches);
        invariantNotNull("Plan Id", id);
        if (!isDeleted) {
            invariantNotNull("Plan name", name);
            invariantNotNull("User Id", userId);
        }
    }

    @Override
    protected void when(PlanEvents event) {
        switch (event) {
            case PlanEvents.PlanCreated e -> {
                this.id = e.planId();
                this.name = e.name();
                this.userId = e.userId();
                this.projects = new HashMap<>();
                this.nextTaskId = 0;
            }
            case PlanEvents.PlanRenamed e -> {
                this.name = e.newName();
            }
            case PlanEvents.ProjectCreated e -> {
                Project project = new Project( e.projectId(), e.projectName(), this.id);
                this.projects.put(e.projectId(), project);
            }
            case PlanEvents.TaskCreated e -> {
                // Find project by ID and add task to it
                Project project = projects.get(e.projectId());
                if (project != null) {
                    Task task = new Task(e.taskId(), e.taskName(), project.getName());
                    project.addTask(task);
                }
                
                // Update nextTaskId to be the next available ID
                try {
                    int taskIdValue = Integer.parseInt(e.taskId());
                    if (taskIdValue >= nextTaskId) {
                        nextTaskId = taskIdValue + 1;
                    }
                } catch (NumberFormatException ex) {
                    // Ignore non-numeric task IDs
                }
            }
            case PlanEvents.TaskChecked e -> {
                // Find project and delegate task checking
                Project project = projects.get(e.projectId());
                if (project != null) {
                    project.checkTask(e.taskId());
                }
            }
            case PlanEvents.TaskUnchecked e -> {
                // Find project and delegate task unchecking
                Project project = projects.get(e.projectId());
                if (project != null) {
                    project.uncheckTask(e.taskId());
                }
            }
            case PlanEvents.TaskDeleted e -> {
                // Find project and delete the task
                Project project = projects.get(e.projectId());
                if (project != null) {
                    project.deleteTask(e.taskId());
                }
            }
            case PlanEvents.TaskDeadlineSet e -> {
                LocalDate deadline = e.deadline() != null ? LocalDate.parse(e.deadline()) : null;

                // Find project and set the task deadline
                Project project = projects.get(e.projectId());
                if (project != null) {
                    project.setTaskDeadline(e.taskId(), deadline);
                }
            }
            case PlanEvents.TaskRenamed e -> {
                // Find project and rename the task
                Project project = projects.get(e.projectId());
                if (project != null) {
                    project.renameTask(e.taskId(), e.newName());
                }
            }
            case PlanEvents.ProjectDeleted e -> {
                this.projects.remove(e.projectId());
            }
            case PlanEvents.PlanDeleted e -> {
                this.isDeleted = true;
            }
            case PlanEvents.TagAssigned e -> {
                // Find project and assign tag to task
                Project project = projects.get(e.projectId());
                if (project != null) {
                    Task task = project.getTask(e.taskId());
                    if (task != null) {
                        task.assignTag(e.tagId());
                    }
                }
            }
            case PlanEvents.TagUnassigned e -> {
                // Find project and unassign tag from task
                Project project = projects.get(e.projectId());
                if (project != null) {
                    Task task = project.getTask(e.taskId());
                    if (task != null) {
                        task.unassignTag(e.tagId());
                    }
                }
            }
        }
    }

    // Helper methods for Contract checks (underscore prefix = PIT exclusion)
    private boolean _planIdMatches(PlanId planId) {
        return getId().equals(planId);
    }

    private boolean _planNameMatches(String value) {
        return getName().equals(value);
    }

    private boolean _planUserMatches(String user) {
        return getUserId().equals(user);
    }

    private boolean _planCreatedEventGenerated(PlanId planId, String name, String userId) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.PlanCreated)
                .map(PlanEvents.PlanCreated.class::cast)
                .map(created -> created.planId().equals(planId)
                        && created.name().equals(name)
                        && created.userId().equals(userId))
                .orElse(false);
    }

    private boolean _notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean _nameDifferent(String newName) {
        return !Objects.equals(this.name, newName);
    }

    private boolean _planRenamedEventGenerated(String newName) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.PlanRenamed)
                .map(PlanEvents.PlanRenamed.class::cast)
                .map(renamed -> renamed.planId().equals(getId())
                        && renamed.newName().equals(newName))
                .orElse(false);
    }

    private boolean _projectAbsent(ProjectId projectId) {
        return !projects.containsKey(projectId);
    }

    private boolean _projectExists(ProjectId projectId) {
        return projects.containsKey(projectId);
    }

    private boolean _projectExists(ProjectName projectName) {
        return getProject(projectName) != null;
    }

    private boolean _projectReferenceExists(Project project) {
        return project != null;
    }

    private boolean _projectNameMatches(ProjectId projectId, ProjectName projectName) {
        return getProject(projectId).getName().equals(projectName);
    }

    private boolean _projectCreatedEventGenerated(ProjectId projectId, ProjectName projectName) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.ProjectCreated)
                .map(PlanEvents.ProjectCreated.class::cast)
                .map(created -> created.planId().equals(getId())
                        && created.projectId().equals(projectId)
                        && created.projectName().equals(projectName))
                .orElse(false);
    }

    private boolean _projectHasTask(Project project, TaskId taskId) {
        return project != null && project.hasTask(taskId);
    }

    private boolean _projectHasNoTasks(Project project) {
        return project.getTasks().isEmpty();
    }

    private boolean _taskNameMatches(Project project, TaskId taskId, String taskName) {
        return project.getTask(taskId).getName().equals(taskName);
    }

    private boolean _taskCreatedEventGenerated(ProjectId projectId, TaskId taskId, String taskName) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.TaskCreated)
                .map(PlanEvents.TaskCreated.class::cast)
                .map(created -> created.planId().equals(getId())
                        && created.projectId().equals(projectId)
                        && created.taskId().equals(taskId)
                        && created.taskName().equals(taskName))
                .orElse(false);
    }

    private boolean _taskNotDone(Task task) {
        return task != null && !task.isDone();
    }

    private boolean _taskDone(Task task) {
        return task != null && task.isDone();
    }

    private boolean _taskDone(Project project, TaskId taskId) {
        return project.getTask(taskId).isDone();
    }

    private boolean _taskNotDone(Project project, TaskId taskId) {
        return !project.getTask(taskId).isDone();
    }

    private boolean _taskCheckedEventGenerated(ProjectId projectId, TaskId taskId) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.TaskChecked)
                .map(PlanEvents.TaskChecked.class::cast)
                .map(checked -> checked.planId().equals(getId())
                        && checked.projectId().equals(projectId)
                        && checked.taskId().equals(taskId))
                .orElse(false);
    }

    private boolean _taskUncheckedEventGenerated(ProjectId projectId, TaskId taskId) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.TaskUnchecked)
                .map(PlanEvents.TaskUnchecked.class::cast)
                .map(unchecked -> unchecked.planId().equals(getId())
                        && unchecked.projectId().equals(projectId)
                        && unchecked.taskId().equals(taskId))
                .orElse(false);
    }

    private boolean _projectDeletedEventGenerated(ProjectId projectId) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.ProjectDeleted)
                .map(PlanEvents.ProjectDeleted.class::cast)
                .map(deleted -> deleted.planId().equals(getId())
                        && deleted.projectId().equals(projectId))
                .orElse(false);
    }

    private boolean _taskDeletedFromProject(Project project, TaskId taskId) {
        return !project.hasTask(taskId);
    }

    private boolean _taskDeletedEventGenerated(ProjectId projectId, TaskId taskId) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.TaskDeleted)
                .map(PlanEvents.TaskDeleted.class::cast)
                .map(deleted -> deleted.planId().equals(getId())
                        && deleted.projectId().equals(projectId)
                        && deleted.taskId().equals(taskId))
                .orElse(false);
    }

    private boolean _taskDeadlineMatches(Project project, TaskId taskId, LocalDate deadline) {
        return Objects.equals(project.getTask(taskId).getDeadline(), deadline);
    }

    private boolean _taskDeadlineSetEventGenerated(ProjectId projectId, TaskId taskId, LocalDate deadline) {
        String expected = deadline != null ? deadline.toString() : null;
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.TaskDeadlineSet)
                .map(PlanEvents.TaskDeadlineSet.class::cast)
                .map(set -> set.planId().equals(getId())
                        && set.projectId().equals(projectId)
                        && set.taskId().equals(taskId)
                        && Objects.equals(set.deadline(), expected))
                .orElse(false);
    }

    private boolean _taskRenamedEventGenerated(ProjectId projectId, TaskId taskId, String newName) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.TaskRenamed)
                .map(PlanEvents.TaskRenamed.class::cast)
                .map(renamed -> renamed.planId().equals(getId())
                        && renamed.projectId().equals(projectId)
                        && renamed.taskId().equals(taskId)
                        && renamed.newName().equals(newName))
                .orElse(false);
    }

    private boolean _planNotDeleted() {
        return !isDeleted;
    }

    private boolean _planDeletedFlagSet() {
        return isDeleted();
    }

    private boolean _planDeletedEventGenerated() {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.PlanDeleted)
                .map(PlanEvents.PlanDeleted.class::cast)
                .map(deleted -> deleted.planId().equals(getId()))
                .orElse(false);
    }

    private boolean _tagAssigned(Task task, TagId tagId) {
        return task.hasTag(tagId);
    }

    private boolean _tagNotAssigned(Task task, TagId tagId) {
        return !task.hasTag(tagId);
    }

    private boolean _tagAssignedEventGenerated(ProjectId projectId, TaskId taskId, TagId tagId) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.TagAssigned)
                .map(PlanEvents.TagAssigned.class::cast)
                .map(assigned -> assigned.planId().equals(getId())
                        && assigned.projectId().equals(projectId)
                        && assigned.taskId().equals(taskId)
                        && assigned.tagId().equals(tagId))
                .orElse(false);
    }

    private boolean _tagUnassigned(Task task, TagId tagId) {
        return !task.hasTag(tagId);
    }

    private boolean _tagUnassignedEventGenerated(ProjectId projectId, TaskId taskId, TagId tagId) {
        return getLastDomainEvent()
                .filter(event -> event instanceof PlanEvents.TagUnassigned)
                .map(PlanEvents.TagUnassigned.class::cast)
                .map(unassigned -> unassigned.planId().equals(getId())
                        && unassigned.projectId().equals(projectId)
                        && unassigned.taskId().equals(taskId)
                        && unassigned.tagId().equals(tagId))
                .orElse(false);
    }

    private boolean _categoryMatches() {
        return getCategory().equals(CATEGORY);
    }
}
